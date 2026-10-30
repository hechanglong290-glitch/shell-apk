#!/system/bin/sh
export PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin:/data/adb/magisk:$PATH

# 1. 尝试挂载读写并删除 jarvis_daemon.sh (兼容安卓9根目录挂载机制)
mount -o remount,rw / 2>/dev/null
mount -o remount,rw /system 2>/dev/null

if [ -f /system/bin/jarvis_daemon.sh ]; then
    rm -f /system/bin/jarvis_daemon.sh
fi

# 2. 如果只读删不掉，写入完整的 Magisk 模块覆盖屏蔽
if [ -f /system/bin/jarvis_daemon.sh ]; then
    mkdir -p /data/adb/modules/remove_jarvis/system/bin
    cat << 'EOF' > /data/adb/modules/remove_jarvis/module.prop
id=remove_jarvis
name=Remove Jarvis
version=v1
versionCode=1
author=admin
description=Disable jarvis_daemon.sh
EOF
    touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
    chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
fi

# 3. 写入开机自动限速脚本（重启后依然永久限制上传在 1MB/s 以内）
mkdir -p /data/adb/service.d
cat << 'EOF' > /data/adb/service.d/limit_upload.sh
#!/system/bin/sh
export PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin:$PATH
sleep 20

# 方案A：通过 tc 限制有线(eth0)和无线(wlan0)上行最高 8mbit (即 1MB/s)
for dev in eth0 wlan0; do
    if [ -d "/sys/class/net/$dev" ]; then
        tc qdisc del dev $dev root 2>/dev/null
        tc qdisc add dev $dev root tbf rate 8mbit burst 64kbit latency 50ms 2>/dev/null
    fi
done

# 方案B：防止盒子内核不支持 tc，用 iptables 限制大包上传速率兜底 (约 1MB/s)
iptables -D OUTPUT -m length --length 500:65535 -m hashlimit --hashlimit-above 800/sec --hashlimit-burst 100 --hashlimit-mode srcip --hashlimit-name up_limit -j DROP 2>/dev/null
iptables -A OUTPUT -m length --length 500:65535 -m hashlimit --hashlimit-above 800/sec --hashlimit-burst 100 --hashlimit-mode srcip --hashlimit-name up_limit -j DROP 2>/dev/null
EOF

chmod 755 /data/adb/service.d/limit_upload.sh

# 4. 刷写磁盘缓存并强制执行重启（三重保障，确保100%重启）
sync
setprop sys.powerctl reboot
/system/bin/reboot
reboot -f
