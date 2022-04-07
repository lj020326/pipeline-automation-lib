#!/usr/bin/env bash

bundle_file=VMware-ovftool-4.3.0-7948156-lin.x86_64.bundle
bundle_file_archive="${bundle_file}.tgz"
bundle_url="http://pxeboot.johnson.int:9080/vmware/${bundle_file_archive}"
bin=./ovftool/bin/ovftool

echo "PWD=${PWD}"

if [ ! -f ${bundle_file_archive} ]
then
    echo "fetching bundle"
    wget -nv "${bundle_url}"
fi

if [ ! -f ${bundle_file} ]
then
    echo "extracting bundle"
    tar -xzvf ${bundle_file_archive}
fi

if [ ! -f $bin ]
then
    echo "running bundle installer"
    sh ${bundle_file} --eulas-agreed --required --console -x ${PWD}/ovftool/lib
    mkdir -p ${PWD}/ovftool/bin
    chmod +x ${PWD}/ovftool/lib/vmware-ovftool/ovftool*
    chmod +x ${PWD}/ovftool/lib/vmware-installer/vmware-installer
    ln -s ${PWD}/ovftool/lib/vmware-ovftool/ovftool ${PWD}/ovftool/bin/ovftool
    ln -s ${PWD}/ovftool/lib/vmware-installer/vmware-installer ${PWD}/ovftool/bin/vmware-installer
fi

