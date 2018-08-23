package com.csdn;

import com.csdn.connection.ConnectedVimServiceBase;
import com.csdn.connection.helpers.GetMOREF;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.VirtualMachineSummary;

import java.util.Map;

public class Connect extends ConnectedVimServiceBase {
    public Connect(String url, String userName, String password) {
        connection.setUrl(url);         //这里的url格式为https://vcenterip/sdk
        connection.setUsername(userName);   //vcenter的用户名
        connection.setPassword(password);  //对应的密码
        connection.connect();

        //检查是否登录成功
        System.out.println(connection.getServiceContent().getAbout().getInstanceUuid());
    }

    void getAllVirtualMachine() {
        try {
            //实例化getMOREFs，这行代码是否需要，取决于如何调用这个函数，如果是在其他类调用这个函数，则需要这行代码，否则不需要
            getMOREFs = new GetMOREF(connection);
            //获取数据中心的引用
            ManagedObjectReference dcMor = getMOREFs.inContainerByType(connection.getServiceContent().getRootFolder(),
                    "Datacenter").get("dataCenter");
            //获取数据中心下的所有主机
            Map<String, ManagedObjectReference> hosts =
                    getMOREFs.inContainerByType(dcMor, "HostSystem");
            //获取指定主机的引用
            ManagedObjectReference hostMor = hosts.get("172.17.7.254");
            //获取主机下的虚拟机数量
            Map<String, ManagedObjectReference> vms =
                        getMOREFs.inContainerByType(hostMor, "VirtualMachine");

            System.out.println(vms.size());
        } catch (InvalidPropertyFaultMsg invalidPropertyFaultMsg) {
            invalidPropertyFaultMsg.printStackTrace();
        } catch (RuntimeFaultFaultMsg runtimeFaultFaultMsg) {
            runtimeFaultFaultMsg.printStackTrace();
        }
    }


    void getVirtualMachineConfig() {
        //ServiceContent这个类跟mob首页的ServiceContent对应起来的，这里相当于拿到一个容器
        ManagedObjectReference propCol = connection.getServiceContent().getPropertyCollector();
        getMOREFs = new GetMOREF(connection);
        try {
            //根据虚拟机名称拿到对应的引用
            ManagedObjectReference vmRef = getMOREFs.vmByVMname("CentOS6.5", propCol);
            //解析虚拟机summary属性
            VirtualMachineSummary vmSummary = (VirtualMachineSummary)
                    getMOREFs.entityProps(vmRef, new String[]{"summary"}).get("summary");
            System.out.println(vmSummary.getConfig().getMemorySizeMB());
            System.out.println(vmSummary.getConfig().getNumCpu());
        } catch (InvalidPropertyFaultMsg invalidPropertyFaultMsg) {
            invalidPropertyFaultMsg.printStackTrace();
        } catch (RuntimeFaultFaultMsg runtimeFaultFaultMsg) {
            runtimeFaultFaultMsg.printStackTrace();
        }

    }
}
