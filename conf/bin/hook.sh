#!/bin/bash

function before_appstart_hook {
#    do something other for example call other script
    dir_name=`dirname $0`
    echo "hook.sh dirname=${dir_name}"
    echo 'In before_appstart_hook sofa boot before_appstart_hook executed'
    SYS_PROPS="$SYS_PROPS --add-opens java.base/*=ALL-UNNAMED -Djdk.tls.maxHandshakeMessageSize=51200"
}

function after_appstart_hook {
    dir_name=`dirname $0`
    echo "hook.sh dirname=${dir_name}"
    echo 'SOFA Boot after_appstart_hook executed'
}

function before_appkill_hook {
#    do something other for example call other script
    dir_name=`dirname $0`
    echo "hook.sh dirname=${dir_name}"
    echo 'In before_appkill_hook sofa boot before_appkill_hook executed'
    #    enable fast class loader
    SOFABOOT_JAR_LAUNCHER="com.alipay.sofa.loader.spring.IndexJarLauncher"
}

function after_appkill_hook {
    dir_name=`dirname $0`
    echo "hook.sh dirname=${dir_name}"
    echo 'SOFA Boot after_appkill_hook executed'
}

function after_profile_hook {
    dir_name=`dirname $0`
    echo "current spring profile=${SPRING_PROFILES_ACTIVE}"
    echo "hook.sh dirname=${dir_name}"
    echo "current env=${LOCAL_ENV}"
    echo 'SOFA Boot after_profile_hook executed'
}
