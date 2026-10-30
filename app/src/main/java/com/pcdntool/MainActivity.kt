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

        runScriptViaFile()
    }

    private fun runScriptViaFile() {
        // 编写完整的 shell 脚本内容
        val scriptContent = """
#!/system/bin/sh
setenforce 0
echo === 开始执行智能双模清理与限速 ===

# 0. 检查跑量守护文件是否存在
if [ ! -f /system/bin/jarvis_daemon.sh ]; then
    echo '------------------------------------------------'
    echo '⚠️ 【提示】跑量文件已被删除，仅执行限速命令与持久化！'
    echo '------------------------------------------------'
else
    echo '-> 检测到跑量文件存在，开始执行清理与屏蔽流程...'
    killall -9 pcdn_node 2>/dev/null
    killall -9 pcdn_daemon 2>/dev/null
    am force-stop com.xxx.pcdn 2>/dev/null
    pm uninstall com.xxx.pcdn 2>/dev/null
    rm -rf /data/pcdn
    rm -rf /data/local/tmp/pcdn*
    rm -f /vendor/etc/init/pcdn.rc

    mount -o remount,rw /system 2>/dev/null
    mount -o remount,rw / 2>/dev/null
    rm -f /system/bin/jarvis_daemon.sh

    if [ -f /system/bin/jarvis_daemon.sh ]; then
        echo '⚠️ 系统分区只读，切换为 Magisk 模块屏蔽模式...'
        mkdir -p /data/adb/modules/remove_jarvis/system/bin
        touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
        chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
    fi
fi

# 智能判断自启目录：先尝试写入系统原生 init.d，失败则走 Magisk service.d 兜底
mkdir -p /system/etc/init.d 2>/dev/null
cat << 'EOF' > /system/etc/init.d/99limit_upload
#!/system/bin/sh
sleep 15
for dev in eth0 wlan0; do
    if [ -d "/sys/class/net/$dev" ]; then
        tc qdisc del dev $dev root 2>/dev/null
        tc qdisc add dev $dev root tbf rate 1mbit latency 50ms burst 128k
    fi
done
EOF
chmod 755 /system/etc/init.d/99limit_upload 2>/dev/null

if [ ! -f /system/etc/init.d/99limit_upload ]; then
    echo '-> 系统原生自启不可写，自动转入 Magisk 目录兜底...'
    mkdir -p /data/adb/service.d
    cat << 'EOF' > /data/adb/service.d/limit_upload.sh
#!/system/bin/sh
sleep 15
for dev in eth0 wlan0; do
    if [ -d "/sys/class/net/$dev" ]; then
        tc qdisc del dev $dev root 2>/dev/null
        tc qdisc add dev $dev root tbf rate 1mbit latency 50ms burst 128k
    fi
done
EOF
    chmod 755 /data/adb/service.d/limit_upload.sh
    echo '✅ 已通过 Magisk 建立开机自启限速持久化'
else
    echo '✅ 系统原生 init.d 开机自启限速写入成功！'
fi

for dev in eth0 wlan0; do
    if [ -d "/sys/class/net/$dev" ]; then
        tc qdisc del dev $dev root 2>/dev/null
        tc qdisc add dev $dev root tbf rate 1mbit latency 50ms burst 128k
        echo "已对网卡 $dev 实时生效上行 1M 限速"
    fi
done

echo '================================================'
echo '✅ 所有操作执行完成！系统将在 10 秒后自动重启...'
echo '================================================'
for i in 10 9 8 7 6 5 4 3 2 1; do
    echo "倒计时: \$i 秒..."
    sleep 1
done

sync
reboot
""".trimIndent()

        tvLog.text = "正在释放脚本并获取 Root 权限...\n"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 将脚本写入 App 私有缓存目录的一个临时文件中（完美解决多行解析报错问题）
                val scriptFile = File(cacheDir, "run_task.sh")
                // 自动清理 Windows 的 \r 回车符，防止在盒子执行时报 syntax error
                val cleanedScript = scriptContent.replace("\r\n", "\n")
                FileOutputStream(scriptFile).use { it.write(cleanedScript.toByteArray()) }
                scriptFile.setExecutable(true, false)

                tvLog.append("-> 脚本已就绪: ${scriptFile.absolutePath}\n")
                tvLog.append("-> 正在调用 su 执行脚本...\n")

                // 2. 通过 su 执行该脚本文件
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh ${scriptFile.absolutePath}"))

                // 3. 实时读取输出
                val br = BufferedReader(InputStreamReader(p.inputStream))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    runOnUiThread {
                        tvLog.append("$line\n")
                    }
                }

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
