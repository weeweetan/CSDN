package com.csdn;

import com.csdn.connection.ConnectedVimServiceBase;

public class Connect extends ConnectedVimServiceBase {
    public Connect(String url, String userName, String password) {
        connection.setUrl(url);         //这里的url格式为https://vcenterip/sdk
        connection.setUsername(userName);   //vcenter的用户名
        connection.setPassword(password);  //对应的密码
        connection.connect();

        //检查是否登录成功
        System.out.println(connection.getServiceContent().getAbout().getInstanceUuid());
    }
}
