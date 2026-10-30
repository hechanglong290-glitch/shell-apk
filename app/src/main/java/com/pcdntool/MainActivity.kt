package com.pcdntool

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvLog = findViewById(R.id.tvLog)

        // 打开 App 瞬间直接自动执行脚本，实时将日志输出到黑框
        runScriptAutomatically()
    }

    private fun runScriptAutomatically() {
        val script = "\n" +
            "setenforce 0\n" +
            "echo === 开始执行智能双模清理与限速 ===\n" +
            "\n" +
            "# 0. 检查跑量守护文件是否存在\n" +
            "if [ ! -f /system/bin/jarvis_daemon.sh ]; then\n" +
            "    echo '------------------------------------------------'\n" +
            "    echo '⚠️ 【提示】跑量文件已被删除，仅执行限速命令与持久化！'\n" +
            "    echo '------------------------------------------------'\n" +
            "else\n" +
            "    echo '-> 检测到跑量文件存在，开始执行清理与屏蔽流程...'\n" +
            "    # 1. 强杀并卸载 PCDN 相关应用及残留\n" +
            "    killall -9 pcdn_node 2>/dev/null\n" +
            "    killall -9 pcdn_daemon 2>/dev/null\n" +
            "    am force-stop com.xxx.pcdn 2>/dev/null\n" +
            "    pm uninstall com.xxx.pcdn 2>/dev/null\n" +
            "    rm -rf /data/pcdn\n" +
            "    rm -rf /data/local/tmp/pcdn*\n" +
            "    rm -f /vendor/etc/init/pcdn.rc\n" +
            "\n" +
            "    # 2. 尝试挂载系统分区并直接删除目标文件\n" +
            "    mount -o remount,rw /system 2>/dev/null\n" +
            "    mount -o remount,rw / 2>/dev/null\n" +
            "    rm -f /system/bin/jarvis_daemon.sh\n" +
            "\n" +
            "    # 3. 如果系统分区无法删除，切换为 Magisk 屏蔽模式\n" +
            "    if [ -f /system/bin/jarvis_daemon.sh ]; then\n" +
            "        echo '⚠️ 系统分区只读，切换为 Magisk 模块屏蔽模式...'\n" +
            "        mkdir -p /data/adb/modules/remove_jarvis/system/bin\n" +
            "        touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh\n" +
            "        chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh\n" +
            "    fi\n" +
            "fi\n" +
            "\n" +
            "# 4. 智能判断自启目录：先尝试写入系统原生 init.d，失败则走 Magisk service.d 兜底\n" +
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
            "chmod 755 /system/etc/init.d/99limit_upload 2>/dev/null\n" +
            "\n" +
            "if [ ! -f /system/etc/init.d/99limit_upload ]; then\n" +
            "    echo '-> 系统原生自启不可写，自动转入 Magisk 目录兜底...'\n" +
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
            "    echo '✅ 已通过 Magisk 建立开机自启限速持久化'\n" +
            "else\n" +
            "    echo '✅ 系统原生 init.d 开机自启限速写入成功！'\n" +
            "fi\n" +
            "\n" +
            "# 5. 立即在当前内存中对有线(eth0)和无线(wlan0)应用上限 1M 上行限速\n" +
            "for dev in eth0 wlan0; do\n" +
            "    if [ -d \"/sys/class/net/\$dev\" ]; then\n" +
            "        tc qdisc del dev \$dev root 2>/dev/null\n" +
            "        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k\n" +
            "        echo \"已对网卡 \$dev 实时生效上行 1M 限速\"\n" +
            "    fi\n" +
            "done\n" +
            "\n" +
            "# 6. 倒数 10 秒准备重启\n" +
            "echo '================================================'\n" +
            "echo '✅ 所有操作执行完成！系统将在 10 秒后自动重启...'\n" +
            "echo '================================================'\n" +
            "for i in 10 9 8 7 6 5 4 3 2 1; do\n" +
            "    echo \"倒计时: \$i 秒...\"\n" +
            "    sleep 1\n" +
            "done\n" +
            "\n" +
            "sync\n" +
            "reboot\n"

        tvLog.text = "正在请求 Root 权限并自动执行脚本...\n"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
                
                // 实时读取标准输出
                val br = BufferedReader(InputStreamReader(p.inputStream))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    runOnUiThread {
                        tvLog.append("$line\n")
                    }
                }
                
                // 实时读取错误输出
                val brErr = BufferedReader(InputStreamReader(p.errorStream))
                while (brErr.readLine().also { line = it } != null) {
                    runOnUiThread {
                        tvLog.append("ERR:$line\n")
                    }
                }
                p.waitFor()
            } catch (e: Exception) {
                runOnUiThread {
                    tvLog.append("异常：${e.message}\n")
                }
            }
        }
    }
}
