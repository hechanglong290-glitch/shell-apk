val script = "\n" +
            "setenforce 0\n" +
            "echo === 开始执行纯系统级清理与限速 ===\n" +
            "\n" +
            "# 1. 强杀并卸载 PCDN 相关应用及残留\n" +
            "killall -9 pcdn_node\n" +
            "killall -9 pcdn_daemon\n" +
            "am force-stop com.xxx.pcdn\n" +
            "pm uninstall com.xxx.pcdn\n" +
            "rm -rf /data/pcdn\n" +
            "rm -rf /data/local/tmp/pcdn*\n" +
            "rm -f /vendor/etc/init/pcdn.rc\n" +
            "\n" +
            "# 2. 尝试将系统挂载为读写，直接清理 /system 目录下的目标文件\n" +
            "mount -o remount,rw /system 2>/dev/null\n" +
            "mount -o remount,rw / 2>/dev/null\n" +
            "rm -f /system/bin/jarvis_daemon.sh\n" +
            "\n" +
            "# 3. 写入纯系统自带的 init.d 开机自启限速脚本（不走 Magisk）\n" +
            "mkdir -p /system/etc/init.d\n" +
            "cat << 'EOF' > /system/etc/init.d/99limit_upload\n" +
            "#!/system/bin/sh\n" +
            "sleep 15\n" +
            "for dev in eth0 wlan0; do\n" +
            "    if [ -d \"/sys/class/net/\$dev\" ]; then\n" +
            "        tc qdisc del dev \$dev root 2>/dev/null\n" +
            "        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k\n" +
            "    fi\n" +
            "done\n" +
            "EOF\n" +
            "\n" +
            "# 赋予 init.d 脚本可执行权限\n" +
            "chmod 755 /system/etc/init.d/99limit_upload\n" +
            "echo '已写入系统自带的 /system/etc/init.d/99limit_upload'\n" +
            "\n" +
            "# 4. 立即在当前内存中对有线(eth0)和无线(wlan0)应用上限 1M 上行限速\n" +
            "for dev in eth0 wlan0; do\n" +
            "    if [ -d \"/sys/class/net/\$dev\" ]; then\n" +
            "        tc qdisc del dev \$dev root 2>/dev/null\n" +
            "        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k\n" +
            "echo \"已对网卡 \$dev 实时生效上行 1M 限速\"\n" +
            "    fi\n" +
            "done\n" +
            "\n" +
            "echo === 所有纯系统级操作执行完成 ===\n"
