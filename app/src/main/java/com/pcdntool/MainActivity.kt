val script = "\n" +
            "setenforce 0\n" +
            "echo === 开始执行智能双模清理与限速 ===\n" +
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
            "# 2. 尝试挂载系统分区并直接删除 /system 目录下的目标文件\n" +
            "mount -o remount,rw /system 2>/dev/null\n" +
            "mount -o remount,rw / 2>/dev/null\n" +
            "rm -f /system/bin/jarvis_daemon.sh\n" +
            "\n" +
            "# 3. 智能判断：先尝试写入系统自带的 init.d 开机自启\n" +
            "mkdir -p /system/etc/init.d 2>/dev/null\n" +
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
            "chmod 755 /system/etc/init.d/99limit_upload 2>/dev/null\n" +
            "\n" +
            "# 4. 检查系统写入是否成功。如果不成功（说明系统只读或挂载失败），自动降级走 Magisk 目录兜底！\n" +
            "if [ ! -f /system/etc/init.d/99limit_upload ]; then\n" +
            "    echo '⚠️ 系统分区无法写入，自动切换为 Magisk 模块与 service.d 兜底模式...'\n" +
            "    \n" +
            "    # 启用 Magisk 模块屏蔽目标文件\n" +
            "    mkdir -p /data/adb/modules/remove_jarvis/system/bin\n" +
            "    touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh\n" +
            "    chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh\n" +
            "    \n" +
            "    # 写入 Magisk 开机自启服务目录\n" +
            "    mkdir -p /data/adb/service.d\n" +
            "    cat << 'EOF' > /data/adb/service.d/limit_upload.sh\n" +
            "#!/system/bin/sh\n" +
            "sleep 15\n" +
            "for dev in eth0 wlan0; do\n" +
            "    if [ -d \"/sys/class/net/\$dev\" ]; then\n" +
            "        tc qdisc del dev \$dev root 2>/dev/null\n" +
            "        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k\n" +
            "    fi\n" +
            "done\n" +
            "EOF\n" +
            "    chmod 755 /data/adb/service.d/limit_upload.sh\n" +
            "    echo '✅ 已成功通过 Magisk service.d 建立持久化兜底'\n" +
            "else\n" +
            "    echo '✅ 系统原生 init.d 开机自启写入成功！'\n" +
            "fi\n" +
            "\n" +
            "# 5. 立即在当前内存中对有线(eth0)和无线(wlan0)应用上限 1M 的上行限速\n" +
            "for dev in eth0 wlan0; do\n" +
            "    if [ -d \"/sys/class/net/\$dev\" ]; then\n" +
            "        tc qdisc del dev \$dev root 2>/dev/null\n" +
            "        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k\n" +
            "        echo \"已对网卡 \$dev 实时生效上行 1M 限速（仅限上传）\"\n" +
            "    fi\n" +
            "done\n" +
            "\n" +
            "echo === 所有清理与限速操作执行完成 ===\n"
