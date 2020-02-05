#!/bin/bash
#
# Setup SUT which may involve downloading, patching, installing dependencies and building
# The goal is to have a runnable SUT

# SCRIPT_DIR should correpond to dtls-fuzzer's root directory
readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
# dir where the suts are stored
readonly SUTS_DIR="$SCRIPT_DIR/suts"
# dir where precomplied suts are stored
readonly SUT_JAR_DIR="$SCRIPT_DIR/experiments/suts"
readonly PATCHES_DIR="$SCRIPT_DIR/experiments/patches"
# dir where the modules SUTs rely on are stored
readonly MODULES_DIR="$SCRIPT_DIR/modules"

# the names of the suts for which directories are created should be consistent with the names appearing in argument files
# variable naming allows us to determine via dynamic variable resolution whether, for exemple, an SUT is fetched from a repository or from some archive
readonly MBEDTLS="mbedtls"
readonly MBEDTLS_ARCH_URL='https://tls.mbed.org/download/mbedtls-2.16.1-gpl.tgz'
readonly GNUTLS_NEW='gnutls-3.6.7'
readonly GNUTLS_NEW_ARCH_URL='ftp://ftp.gnutls.org/gcrypt/gnutls/v3.6/gnutls-3.6.7.tar.xz'
readonly GNUTLS_OLD='gnutls-3.5.19'
readonly GNUTLS_OLD_ARCH_URL='ftp://ftp.gnutls.org/gcrypt/gnutls/v3.5/gnutls-3.5.19.tar.xz'
readonly OPENSSL='openssl-1.1.1b'
readonly OPENSSL_ARCH_URL='https://www.openssl.org/source/old/1.1.1/openssl-1.1.1b.tar.gz'
readonly ETINYDTLS='etinydtls'
readonly ETINYDTLS_REP_URL='https://github.com/eclipse/tinydtls.git'
readonly ETINYDTLS_COMMIT='8414f8a'
readonly CTINYDTLS='ctinydtls'
readonly CTINYDTLS_REP_URL='https://github.com/contiki-ng/tinydtls.git'
readonly CTINYDTLS_COMMIT='53a0d97'
readonly SCANDIUM_OLD='scandium-2.0.0'
readonly SCANDIUM_OLD_JAR_PATH="$SUT_JAR_DIR/scandium-2.0.0-dtls-server.jar"
#readonly SCANDIUM_OLD_JVM="jdk-12.0.2"
readonly SCANDIUM_NEW='scandium-2.0.0_latest'
readonly SCANDIUM_NEW_JAR_PATH="$SUT_JAR_DIR/scandium-2.0.0-dtls-server_latest.jar"
readonly JSSE_12="jsse-12"
readonly JSSE_12_JVM_URL="https://download.java.net/java/GA/jdk12.0.2/e482c34c86bd4bf8b56c0b35558996b9/10/GPL/openjdk-12.0.2_linux-x64_bin.tar.gz"

sutvarnames=("OPENSSL" "MBEDTLS" "ETINYDTLS" "CTINYDTLS" "GNUTLS_OLD" "GNUTLS_NEW" "SCANDIUM_OLD" "SCANDIUM_NEW" "JSSE-12")
sut_strings=($OPENSSL $MBEDTLS $ETINYDTLS $CTINYDTLS $GNUTLS_OLD $GNUTLS_NEW $SCANDIUM_OLD $SCANDIUM_NEW $JSSE_12)

function get_sutvarname() {
    for varname in ${sutvarnames[*]}
    do
        sut="${!varname}"
        if [[ "$sut" = "$1" ]]; then
            echo $varname
            return 1
        fi
    done
    echo "Could not find var name for $1"
    exit -1
}

function get_jar_path() {
    sut=$1
    return_var=`get_sutvarname $sut`

    # ok, is our SUT a pre-packaged -jar 
    jar_path_var="$return_var"_JAR_PATH
    jar_path="${!jar_path_var}"
    echo $jar_path
}

function get_rep_url() {
    sut=$1
    return_var=`get_sutvarname $sut`
    
    # ok, is our SUT fetched from a repository?
    rep_url_var="$return_var"_REP_URL
    rep_url="${!rep_url_var}"
    echo $rep_url
}

function solve_arch() {
    sut_dir=$1
    arch_url=$2
    temp_dir=/tmp/`(basename $arch_url)`
    echo $temp_dir
    echo "Fetching/unpacking from $arch_url into $sut_dir"
    if [[ ! -f "$temp_dir" ]]
    then
        echo "Downloading archive from url to $temp_dir"
        wget -nc --no-check-certificate $arch_url -O $temp_dir
    fi
    
    mkdir $sut_dir
    # ${temp_dir##*.} retrieves the substring between the last index of . and the end of $temp_dir
    arch=`echo "${temp_dir##*.}"`
    if [[ $arch == "xz" ]]
    then
        tar_param="-xJf"
    else 
        tar_param="zxvf"
    fi
    echo $tar_param
    tar $tar_param $temp_dir -C $sut_dir --strip-components=1
}

function clone_rep() {
    sut_dir=$1
    rep_url=$2
    rep_com=$3
    
    echo "Cloning repository $rep_url commit $rep_com to $sut_dir"
    git clone $rep_url $sut_dir
    ( cd $sut_dir ; git checkout $rep_com ) #; rm -rf $sut_dir/.git )
}

# Downloads the SUT source files and places them in a SUT directory 
function download_sut() {
    sut=$1
    sut_dir=$2

    echo "Downloading files for $sut"
    # updates $sut_varname
    return_var=`get_sutvarname $sut`
    
    # ok, is our SUT fetched from a repository?
    rep_url_var="$return_var"_REP_URL
    rep_url="${!rep_url_var}"
    if [[ -n "$rep_url" ]]; then
        rep_com_var="$return_var"_COMMIT
        rep_com="${!rep_com_var}"
        clone_rep $sut_dir $rep_url $rep_com
        repo="$sut"
    fi

    # maybe the SUT is retrieved from a URL pointing to some archive
    arch_url_var="$return_var"_ARCH_URL
    arch_url="${!arch_url_var}"
    if [[ -n "$arch_url" ]]; then 
        solve_arch $sut_dir $arch_url
    fi

    ## maybe the SUT is packaged as a .jar so we simply have to copy it
    #jar_path=`get_jar_path $sut`
    #if [[ -n "$jar_path"]]; then
    #    mkdir $sut_dir
    #    echo "Copying sut"
    #fi
}


# Applies patches for SUTs that require them
function apply_patch() {
    sut=$1
    sut_dir=$2
    #$(echo $sut | cut -d '-' -f 1)
    sut_patch=$PATCHES_DIR/$sut.patch
    
    if [[ -f $sut_patch ]] 
    then
        echo "Applying patch $sut_patch"
        rep_url=`get_rep_url $sut`
        if [[ -n "$rep_url" ]]; then
            echo "via git apply"
            ( cd $sut_dir; git apply $sut_patch )
        else 
            echo "via patch"
            patch -s -p0 < $sut_patch
        fi
    fi
}

# Builds the SUT. In this process also installs any necessary dependancies
function make_sut() {
    sut=$1
    sut_dir=$2

    if [[ -d MODULES_DIR ]]; then
        mkdir $MODULES_DIR
    fi

    # Java SUT, meaning all we need to ensure is that the right vm is installed
    jar_path=`get_jar_path $sut`
    if [[ -n "$jar_path"]]; then
        echo "Deploying JVM"
    fi
}

function setup_sut() {
    sut_dir=$SUTS_DIR/$1

    if [[ ! -d "$SUTS_DIR" ]]
    then
        echo "Creating SUTs directory $SUTS_DIR"
        mkdir $SUTS_DIR
    fi
    if [[ -d $sut_dir ]]
    then
        echo "SUT already exists/was already setup"
        echo "Delete $sut_dir or $SUTS_DIR to re-setup"
        return
    fi
    download_sut $1 $sut_dir 
    apply_patch $1 $sut_dir
}

if [ $# = 0 ]; then
    echo "Usage: setup_sut.sh  [SUT]..."
    echo "Where SUT is an element in: "
    for ix in ${!sut_strings[*]}
    do
        printf "   %s\n" "${sut_strings[$ix]}"
    done
    exit 1
else
    for sut in "$@"
    do 
        if [[ ! " ${sut_strings[@]} " =~ " ${sut} " ]]; then
            echo "$sut not recognized"
        else 
            setup_sut $sut
        fi
    done
fi
