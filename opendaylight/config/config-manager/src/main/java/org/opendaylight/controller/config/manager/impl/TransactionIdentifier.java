/*
 * Copyright (c) 2013, 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.manager.impl;

import org.opendaylight.yangtools.concepts.Identifier;

public class TransactionIdentifier implements Identifier {
    private static final long serialVersionUID = 1L;
    private final String name;

    public TransactionIdentifier(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "TransactionIdentifier{" + "name='" + name + '\'' + '}';
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        TransactionIdentifier that = (TransactionIdentifier) object;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
