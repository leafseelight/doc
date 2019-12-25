#!/bin/bash

#本地端口转发 比如可以连接到生产的数据库
REMOTE_HOST=
export SSHPASS=
sshpass -e ssh -NCfg -L 3388:127.0.0.1:3306 root@$REMOTE_HOST

# 0表示隧道建立成功
echo ============= $?