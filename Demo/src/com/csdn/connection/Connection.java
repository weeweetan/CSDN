/*
 * ******************************************************
 * Copyright VMware, Inc. 2010-2012.  All Rights Reserved.
 * ******************************************************
 *
 * DISCLAIMER. THIS PROGRAM IS PROVIDED TO YOU "AS IS" WITHOUT
 * WARRANTIES OR CONDITIONS # OF ANY KIND, WHETHER ORAL OR WRITTEN,
 * EXPRESS OR IMPLIED. THE AUTHOR SPECIFICALLY # DISCLAIMS ANY IMPLIED
 * WARRANTIES OR CONDITIONS OF MERCHANTABILITY, SATISFACTORY # QUALITY,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.csdn.connection;

import com.vmware.vim25.*;

import java.net.URL;
import java.util.Map;

/**
 * This simple object shows how to set up a vCenter connection. It is intended as a utility class for use
 * by Samples that will need to connect before they can do anything useful. This is a light weight POJO
 * that should be very easy to make portable.
 *
 * @see ConnectedVimServiceBase
 */
public interface Connection {
    // getters and setters
    void setUrl(String url);

    String getUrl();

    String getHost();

    Integer getPort();

    void setUsername(String username);

    String getUsername();

    void setPassword(String password);

    String getPassword();

    VimService getVimService();

    VimPortType getVimPort();

    ServiceContent getServiceContent();

    UserSession getUserSession();

    String getServiceInstanceName();

    @SuppressWarnings("rawtypes")
    Map getHeaders();

    ManagedObjectReference getServiceInstanceReference();

    Connection connect();

    boolean isConnected();

    Connection disconnect();

    URL getURL();
}
