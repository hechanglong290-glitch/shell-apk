#!/system/bin/sh

# 1. 先尝试以读写方式挂载 /system 并直接删除 jarvis_daemon.sh
mount -o remount,rw /system 2>/dev/null

if [ -f /system/bin/jarvis_daemon.sh ]; then
    rm -f /system/bin/jarvis_daemon.sh
fi

# 2. 检查文件是否真的被删掉了（如果 /system 只读导致文件还在，切换到 Magisk 覆盖屏蔽模式）
if [ -f /system/bin/jarvis_daemon.sh ]; then
    mkdir -p /data/adb/modules/remove_jarvis/system/bin
    touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
    chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
fi

# 3. 可以在下方继续追加你的 PCDN 清理或上行限速命令：


# 4. 执行完毕后自动重启生效
reboot
