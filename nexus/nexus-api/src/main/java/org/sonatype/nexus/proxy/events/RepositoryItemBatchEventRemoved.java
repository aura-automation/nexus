/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.proxy.events;

import java.util.Collection;

import org.sonatype.nexus.proxy.repository.Repository;

/**
 * Event fired when a removal of a batch of items is to be announced.
 * 
 * @author cstamas
 * @since 2.3
 */
public abstract class RepositoryItemBatchEventRemoved
    extends RepositoryItemBatchEvent
{
    /**
     * Constructor.
     * 
     * @param repository
     * @param itemPaths
     */
    public RepositoryItemBatchEventRemoved( final Repository repository, final Collection<String> itemPaths )
    {
        super( repository, itemPaths );
    }
}
