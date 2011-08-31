/**
 * Copyright (c) 2008-2011 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://www.sonatype.com/products/nexus/attributions.
 *
 * This program is free software: you can redistribute it and/or modify it only under the terms of the GNU Affero General
 * Public License Version 3 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License Version 3
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License Version 3 along with this program.  If not, see
 * http://www.gnu.org/licenses.
 *
 * Sonatype Nexus (TM) Open Source Version is available from Sonatype, Inc. Sonatype and Sonatype Nexus are trademarks of
 * Sonatype, Inc. Apache Maven is a trademark of the Apache Foundation. M2Eclipse is a trademark of the Eclipse Foundation.
 * All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.log.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.io.RawInputStreamFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.LimitedInputStream;
import org.sonatype.nexus.NexusStreamResponse;
import org.sonatype.nexus.configuration.application.ApplicationConfiguration;
import org.sonatype.nexus.log.DefaultLogConfiguration;
import org.sonatype.nexus.log.LogConfiguration;
import org.sonatype.nexus.log.LogConfigurationParticipant;
import org.sonatype.nexus.log.LogManager;

import com.google.inject.Injector;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.util.StatusPrinter;

//TODO configuration operations should be locking
/**
 * @author cstamas
 * @author juven
 * @author adreghiciu@gmail.com
 */
@Component( role = LogManager.class )
public class LogbackLogManager
    implements LogManager
{

    private static final String KEY_APPENDER_FILE = "appender.file";

    private static final String KEY_APPENDER_PATTERN = "appender.pattern";

    private static final String KEY_ROOT_LEVEL = "root.level";

    private static final String KEY_LOG_CONFIG_DIR = "nexus.log-config-dir";

    private static final String LOG_CONF = "logback.xml";

    private static final String LOG_CONF_PROPS = "logback.properties";

    @Requirement
    private Logger logger;

    @Requirement( role = LogConfigurationParticipant.class )
    private List<LogConfigurationParticipant> logConfigurationParticipants;

    @Requirement
    private Injector injector;

    @Requirement
    private ApplicationConfiguration applicationConfiguration;

    public Set<File> getLogFiles()
    {
        HashSet<File> files = new HashSet<File>();

        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

        for ( Logger l : ctx.getLoggerList() )
        {
            ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) l;
            Iterator<Appender<ILoggingEvent>> it = log.iteratorForAppenders();

            while ( it.hasNext() )
            {
                Appender<ILoggingEvent> ap = it.next();

                if ( ap instanceof FileAppender<?> || ap instanceof RollingFileAppender<?> )
                {
                    FileAppender<?> fileAppender = (FileAppender<?>) ap;
                    String path = fileAppender.getFile();
                    files.add( new File( path ) );
                }
            }
        }

        return files;
    }

    public File getLogFile( String filename )
    {
        Set<File> logFiles = getLogFiles();

        for ( File logFile : logFiles )
        {
            if ( logFile.getName().equals( filename ) )
            {
                return logFile;
            }
        }

        return null;
    }

    public LogConfiguration getConfiguration()
        throws IOException
    {
        Properties logProperties = loadConfigurationProperties();
        DefaultLogConfiguration configuration = new DefaultLogConfiguration();

        configuration.setRootLoggerLevel( logProperties.getProperty( KEY_ROOT_LEVEL ) );
        // TODO
        configuration.setRootLoggerAppenders( "console,file" );
        configuration.setFileAppenderPattern( logProperties.getProperty( KEY_APPENDER_PATTERN ) );
        configuration.setFileAppenderLocation( logProperties.getProperty( KEY_APPENDER_FILE ) );

        return configuration;
    }

    public void setConfiguration( LogConfiguration configuration )
        throws IOException
    {
        Properties logProperties = loadConfigurationProperties();

        logProperties.setProperty( KEY_ROOT_LEVEL, configuration.getRootLoggerLevel() );
        logProperties.setProperty( KEY_APPENDER_PATTERN, configuration.getFileAppenderPattern() );

        saveConfigurationProperties( logProperties );
        // TODO this will do a reconfiguration but would be just enough to "touch" logback.xml"
        reconfigure();
    }

    public Collection<NexusStreamResponse> getApplicationLogFiles()
        throws IOException
    {
        logger.debug( "List log files." );

        Set<File> files = getLogFiles();

        ArrayList<NexusStreamResponse> result = new ArrayList<NexusStreamResponse>( files.size() );

        for ( File file : files )
        {
            NexusStreamResponse response = new NexusStreamResponse();

            response.setName( file.getName() );

            // TODO:
            response.setMimeType( "text/plain" );

            response.setSize( file.length() );

            response.setInputStream( null );

            result.add( response );
        }

        return result;
    }

    /**
     * Retrieves a stream to the requested log file. This method ensures that the file is rooted in the log folder to
     * prevent browsing of the file system.
     * 
     * @param logFile path of the file to retrieve
     * @returns InputStream to the file or null if the file is not allowed or doesn't exist.
     */
    public NexusStreamResponse getApplicationLogAsStream( String logFile, long from, long count )
        throws IOException
    {
        if ( logger.isDebugEnabled() )
        {
            logger.debug( "Retrieving " + logFile + " log file." );
        }

        if ( logFile.contains( File.pathSeparator ) )
        {
            logger.warn( "Nexus refuses to retrieve log files with path separators in its name." );

            return null;
        }

        File log = getLogFile( logFile );

        if ( log == null || !log.exists() )
        {
            logger.warn( "Log file does not exist: [" + logFile + "]" );

            return null;
        }

        NexusStreamResponse response = new NexusStreamResponse();

        response.setName( logFile );

        response.setMimeType( "text/plain" );

        response.setSize( log.length() );

        response.setFromByte( from );

        response.setBytesCount( count );

        response.setInputStream( new LimitedInputStream( new FileInputStream( log ), from, count ) );

        return response;
    }

    @Override
    public void configure()
    {
        // TODO maybe do some optimization that if participants does not change, do not reconfigure
        prepareConfigurationFiles();
        reconfigure();
    }

    private Properties loadConfigurationProperties()
        throws IOException
    {
        prepareConfigurationFiles();
        String logConfigDir = getLogConfigDir();
        File logConfigPropsFile = new File( logConfigDir, LOG_CONF_PROPS );
        InputStream in = null;
        try
        {
            in = new FileInputStream( logConfigPropsFile );

            Properties properties = new Properties();
            properties.load( in );

            return properties;
        }
        finally
        {
            IOUtil.close( in );
        }
    }

    private void saveConfigurationProperties( Properties properties )
        throws FileNotFoundException, IOException
    {
        String logConfigDir = getLogConfigDir();
        File logConfigPropsFile = new File( logConfigDir, LOG_CONF_PROPS );
        OutputStream out = null;
        try
        {
            out = new FileOutputStream( logConfigPropsFile );
            properties.store( out, "Saved by Nexus" );
        }
        finally
        {
            IOUtil.close( out );
        }
    }

    private String getLogConfigDir()
    {
        String logConfigDir = System.getProperty( KEY_LOG_CONFIG_DIR );

        if ( StringUtils.isEmpty( logConfigDir ) )
        {
            logConfigDir = applicationConfiguration.getConfigurationDirectory().getAbsolutePath();

            System.setProperty( KEY_LOG_CONFIG_DIR, logConfigDir );
        }

        return logConfigDir;
    }

    private void prepareConfigurationFiles()
    {
        String logConfigDir = getLogConfigDir();

        File logConfigPropsFile = new File( logConfigDir, LOG_CONF_PROPS );
        if ( !logConfigPropsFile.exists() )
        {
            try
            {
                URL configUrl = this.getClass().getResource( "/META-INF/log/" + LOG_CONF_PROPS );

                FileUtils.copyURLToFile( configUrl, logConfigPropsFile );
            }
            catch ( IOException e )
            {
                throw new IllegalStateException( "Could not create logback.properties as "
                    + logConfigPropsFile.getAbsolutePath() );
            }
        }

        if ( logConfigurationParticipants != null )
        {
            for ( LogConfigurationParticipant participant : logConfigurationParticipants )
            {
                String name = participant.getName();
                File logConfigFile = new File( logConfigDir, name );
                if ( !logConfigFile.exists() )
                {
                    InputStream in = null;
                    try
                    {
                        in = participant.getConfiguration();

                        FileUtils.copyStreamToFile( new RawInputStreamFacade( in ), logConfigFile );
                    }
                    catch ( IOException e )
                    {
                        throw new IllegalStateException( String.format( "Could not create %s as %s", name,
                            logConfigFile.getAbsolutePath() ), e );
                    }
                    finally
                    {
                        IOUtil.close( in );
                    }
                }
            }
        }
        File logConfigFile = new File( logConfigDir, LOG_CONF );
        PrintWriter out = null;
        try
        {
            out = new PrintWriter( logConfigFile );

            out.println( "<?xml version='1.0' encoding='UTF-8'?>" );
            out.println( "<configuration scan='true'>" );
            out.println( "  <property file='${nexus.log-config-dir}/logback.properties'/>" );
            if ( logConfigurationParticipants != null )
            {
                for ( LogConfigurationParticipant participant : logConfigurationParticipants )
                {
                    out.println( String.format( "  <include file='${nexus.log-config-dir}/%s'/>", participant.getName() ) );
                }
            }
            out.write( "</configuration>" );
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( "Could not create logback.xml as " + logConfigFile.getAbsolutePath() );
        }
        finally
        {
            IOUtil.close( out );
        }

    }

    private void reconfigure()
    {
        String logConfigDir = getLogConfigDir();

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        try
        {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext( lc );
            lc.reset();
            configurator.doConfigure( new File( logConfigDir, LOG_CONF ) );
        }
        catch ( JoranException je )
        {
            je.printStackTrace();
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings( lc );
        injectAppenders();
    }

    private void injectAppenders()
    {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

        for ( Logger l : ctx.getLoggerList() )
        {
            ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) l;
            Iterator<Appender<ILoggingEvent>> it = log.iteratorForAppenders();

            while ( it.hasNext() )
            {
                Appender<ILoggingEvent> ap = it.next();
                injector.injectMembers( ap );
            }
        }
    }

}