/**
 * Copyright (c) 2008-2011 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions
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
package org.sonatype.nexus.integrationtests.nexus4594;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.hamcrest.Matchers;
import org.sonatype.nexus.integrationtests.nexus4539.AutoBlockITSupport;

public class Blocked2NFCITSupport
    extends AutoBlockITSupport
{

    /**
     * Check that the Nexus made remote requests to our fake repository.
     */
    protected void verifyNexusWentRemote()
    {
        assertThat( pathsTouched, not( Matchers.<String>empty() ) );
        assertThat( pathsTouched, hasItem( "/repository/foo/bar/5.0/bar-5.0.jar" ) );
        pathsTouched.clear();
    }

    /**
     * Check that the Nexus did not made remote requests to our fake repository.
     */
    protected void verifyNexusDidNotWentRemote()
    {
        assertThat( pathsTouched, Matchers.<String>empty() );
    }

}