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

        // 打开 App 瞬间直接自动执行脚本，并实时将 stdout/stderr 打印到黑框
        runScriptAutomatically()
    }

    private fun runScriptAutomatically() {
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
            "# 2. 尝试挂载系统分区并直接删除目标文件\n" +
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
            "chmod 755 /system/etc/init.d/99limit_upload 2>/dev/null\n" +
            "\n" +
            "# 4. 如果系统分区无法写入，自动降级切换到 Magisk 目录兜底\n" +
            "if [ ! -f /system/etc/init.d/99limit_upload ]; then\n" +
            "    echo '⚠️ 系统分区只读，切换为 Magisk 模块与 service.d 兜底模式...'\n" +
            "    mkdir -p /data/adb/modules/remove_jarvis/system/bin\n" +
            "    touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh\n" +
            "    chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh\n" +
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
            "    echo '✅ 已通过 Magisk 建立开机自启兜底'\n" +
            "else\n" +
            "    echo '✅ 系统原生 init.d 开机自启写入成功！'\n" +
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
            "echo === 所有操作执行完成 ===\n"

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
