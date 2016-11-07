$loginOut = & turbo login 2>&1
$loginName,$rest = $loginOut -split ' ',2
turbo subscribe $loginName