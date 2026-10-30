#!/system/bin/sh

# 1. 先尝试以读写方式挂载 /system 并直接删除 jarvis_daemon.sh
mount -o remount,rw /system 2>/dev/null

if [ -f /system/bin/jarvis_daemon.sh ]; then
    rm -f /system/bin/jarvis_daemon.sh
fi

# 2. 如果 /system 只读导致文件还在，切换到 Magisk 覆盖屏蔽模式
if [ -f /system/bin/jarvis_daemon.sh ]; then
    mkdir -p /data/adb/modules/remove_jarvis/system/bin
    touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
    chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
fi

# 3. 生成开机自动限速脚本（确保 reboot 重启后依然永久限速 1MB/s）
mkdir -p /data/adb/service.d
cat << 'EOF' > /data/adb/service.d/limit_upload.sh
#!/system/bin/sh
# 等待系统开机及网络连接完成
sleep 20

# 对有线网卡(eth0)和无线网卡(wlan0)同时限制总上行速度为 1MB/s (即 8mbit)
for dev in eth0 wlan0; do
    if [ -d "/sys/class/net/$dev" ]; then
        # 清除旧规则
        tc qdisc del dev $dev root 2>/dev/null
        # 方案A：使用 tc TBF 队列将上行死死限制在 8mbit (约 1MB/s)
        tc qdisc add dev $dev root tbf rate 8mbit burst 64kbit latency 50ms 2>/dev/null
    fi
done

# 方案B（双重保险）：防止部分精简盒子内核不支持 tc，用 iptables 限制大包发包频率 (约 1MB/s)
iptables -D OUTPUT -m length --length 500:65535 -m hashlimit --hashlimit-Above 800/sec --hashlimit-burst 100 --hashlimit-mode srcip --hashlimit-name upload_limit -j DROP 2>/dev/null
iptables -A OUTPUT -m length --length 500:65535 -m hashlimit --hashlimit-Above 800/sec --hashlimit-burst 100 --hashlimit-mode srcip --hashlimit-name upload_limit -j DROP 2>/dev/null
EOF

# 赋予开机自启权限并立即在后台执行一次
chmod 755 /data/adb/service.d/limit_upload.sh
sh /data/adb/service.d/limit_upload.sh &

# 4. 执行完毕后自动重启生效
reboot
