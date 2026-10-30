package com.pcdntool

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvLog = findViewById(R.id.tvLog)

        runInteractiveScript()
    }

    private fun runInteractiveScript() {
        // 使用干净的列表拼接，并且对所有 shell 变量使用转义符号 \$dev，彻底避免编译报错
        val scriptContent = listOf(
            "#!/system/bin/sh",
            "setenforce 0",
            "echo === 智能 PCDN 清理与网络限速管理工具 ===",
            "",
            "# 检查跑量守护文件是否存在",
            "if [ -f /system/bin/jarvis_daemon.sh ]; then",
            "    echo '-> 检测到跑量守护脚本存在，开始自动清理与屏蔽...'",
            "    killall -9 pcdn_node 2>/dev/null",
            "    killall -9 pcdn_daemon 2>/dev/null",
            "    am force-stop com.xxx.pcdn 2>/dev/null",
            "    pm uninstall com.xxx.pcdn 2>/dev/null",
            "    rm -rf /data/pcdn",
            "    rm -rf /data/local/tmp/pcdn*",
            "    rm -f /vendor/etc/init/pcdn.rc",
            "",
            "    mount -o remount,rw /system 2>/dev/null",
            "    mount -o remount,rw / 2>/dev/null",
            "    rm -f /system/bin/jarvis_daemon.sh",
            "",
            "    if [ -f /system/bin/jarvis_daemon.sh ]; then",
            "        echo '⚠️ 系统分区只读，切换为 Magisk 模块屏蔽模式...'",
            "        mkdir -p /data/adb/modules/remove_jarvis/system/bin",
            "        touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh",
            "        chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh",
            "    fi",
            "",
            "    echo '================================================'",
            "    echo '✅ 跑量文件已清理完成，系统将在 5 秒后自动重启...'",
            "    echo '================================================'",
            "    sleep 5",
            "    sync",
            "    reboot",
            "else",
            "    echo '------------------------------------------------'",
            "    echo '⚠️ 【提示】此设备没有任何跑量后台运行！'",
            "    echo '------------------------------------------------'",
            "    echo ''",
            "    echo '请选择您的操作：'",
            "    echo '  [1] 限制上传速度 (限制双网卡上行最高 1M)'",
            "    echo '  [2] 取消上传限制 (恢复网络全速)'",
            "    echo '------------------------------------------------'",
            "    ",
            "    printf \"请输入选项数字 (1 或 2): \"",
            "    read choice",
            "    ",
            "    case \"\$choice\" in",
            "        1)",
            "            echo '-> 正在执行：限制上传速度...'",
            "            mkdir -p /system/etc/init.d 2>/dev/null",
            "            echo '#!/system/bin/sh' > /system/etc/init.d/99limit_upload",
            "            echo 'sleep 15' >> /system/etc/init.d/99limit_upload",
            "            echo 'for dev in eth0 wlan0; do' >> /system/etc/init.d/99limit_upload",
            "            echo '    if [ -d \"/sys/class/net/\$dev\" ]; then' >> /system/etc/init.d/99limit_upload",
            "            echo '        tc qdisc del dev \$dev root 2>/dev/null' >> /system/etc/init.d/99limit_upload",
            "            echo '        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k' >> /system/etc/init.d/99limit_upload",
            "            echo '    fi' >> /system/etc/init.d/99limit_upload",
            "            echo 'done' >> /system/etc/init.d/99limit_upload",
            "            chmod 755 /system/etc/init.d/99limit_upload 2>/dev/null",
            "            ",
            "            if [ ! -f /system/etc/init.d/99limit_upload ]; then",
            "                mkdir -p /data/adb/service.d",
            "                echo '#!/system/bin/sh' > /data/adb/service.d/limit_upload.sh",
            "                echo 'sleep 15' >> /data/adb/service.d/limit_upload.sh",
            "                echo 'for dev in eth0 wlan0; do' >> /data/adb/service.d/limit_upload.sh",
            "                echo '    if [ -d \"/sys/class/net/\$dev\" ]; then' >> /data/adb/service.d/limit_upload.sh",
            "                echo '        tc qdisc del dev \$dev root 2>/dev/null' >> /data/adb/service.d/limit_upload.sh",
            "                echo '        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k' >> /data/adb/service.d/limit_upload.sh",
            "                echo '    fi' >> /data/adb/service.d/limit_upload.sh",
            "                echo 'done' >> /data/adb/service.d/limit_upload.sh",
            "                chmod 755 /data/adb/service.d/limit_upload.sh",
            "            fi",
            "            ",
            "            for dev in eth0 wlan0; do",
            "                if [ -d \"/sys/class/net/\$dev\" ]; then",
            "                    tc qdisc del dev \$dev root 2>/dev/null",
            "                    tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k",
            "                    echo \"已对网卡 \$dev 生效上行 1M 限速\"",
            "                fi",
            "            done",
            "            echo '✅ 上传限制已成功开启！'",
            "            ;;",
            "        2)",
            "            echo '-> 正在执行：取消上传限制...'",
            "            rm -f /system/etc/init.d/99limit_upload",
            "            rm -f /data/adb/service.d/limit_upload.sh",
            "            ",
            "            for dev in eth0 wlan0; do",
            "                if [ -d \"/sys/class/net/\$dev\" ]; then",
            "                    tc qdisc del dev \$dev root 2>/dev/null",
            "                    echo \"已清除网卡 \$dev 的限速规则，恢复全速\"",
            "                fi",
            "            done",
            "            echo '✅ 上传限制已解除，网络已恢复全速！'",
            "            ;;",
            "        *)",
            "            echo '❌ 输入无效或未检测到输入，默认不作修改。'",
            "            ;;",
            "    esac",
            "fi"
        ).joinToString("\n")

        tvLog.text = "正在初始化工具并获取 Root 权限...\n"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scriptFile = File(cacheDir, "interactive_task.sh")
                val cleanedScript = scriptContent.replace("\r\n", "\n")
                FileOutputStream(scriptFile).use { it.write(cleanedScript.toByteArray()) }
                scriptFile.setExecutable(true, false)

                tvLog.append("-> 脚本已就绪，正在启动...\n\n")

                val process = ProcessBuilder("su")
                    .redirectErrorStream(true)
                    .start()

                val writer = process.outputStream.bufferedWriter()
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                writer.write("sh ${scriptFile.absolutePath}\n")
                writer.flush()

                val buffer = CharArray(1024)
                var read: Int
                while (reader.read(buffer).also { read = it } != -1) {
                    val output = String(buffer, 0, read)
                    runOnUiThread {
                        tvLog.append(output)
                    }
                }

                process.waitFor()
            } catch (e: Exception) {
                runOnUiThread {
                    tvLog.append("\n异常错误：${e.message}\n")
                }
            }
        }
    }
}
