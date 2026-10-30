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
        // 使用支持用户交互输入（read）的 Shell 脚本
        val scriptContent = """
#!/system/bin/sh
setenforce 0
echo === 智能 PCDN 清理与网络限速管理工具 ===

# 检查跑量守护文件是否存在
if [ -f /system/bin/jarvis_daemon.sh ]; then
    echo '-> 检测到跑量守护脚本存在，开始自动清理与屏蔽...'
    
    # 1. 强杀并卸载 PCDN 相关应用及残留
    killall -9 pcdn_node 2>/dev/null
    killall -9 pcdn_daemon 2>/dev/null
    am force-stop com.xxx.pcdn 2>/dev/null
    pm uninstall com.xxx.pcdn 2>/dev/null
    rm -rf /data/pcdn
    rm -rf /data/local/tmp/pcdn*
    rm -f /vendor/etc/init/pcdn.rc

    # 2. 尝试挂载系统分区并直接删除目标文件
    mount -o remount,rw /system 2>/dev/null
    mount -o remount,rw / 2>/dev/null
    rm -f /system/bin/jarvis_daemon.sh

    # 3. 如果系统分区只读，切换为 Magisk 模块屏蔽模式
    if [ -f /system/bin/jarvis_daemon.sh ]; then
        echo '⚠️ 系统分区只读，切换为 Magisk 模块屏蔽模式...'
        mkdir -p /data/adb/modules/remove_jarvis/system/bin
        touch /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
        chmod 000 /data/adb/modules/remove_jarvis/system/bin/jarvis_daemon.sh
    fi

    echo '================================================'
    echo '✅ 跑量文件已清理完成，系统将在 5 秒后自动重启...'
    echo '================================================'
    sleep 5
    sync
    reboot
else
    echo '------------------------------------------------'
    echo '⚠️ 【提示】此设备没有任何跑量后台运行！'
    echo '------------------------------------------------'
    echo ''
    echo '请选择您的操作：'
    echo '  [1] 限制上传速度 (限制双网卡上行最高 1M)'
    echo '  [2] 取消上传限制 (恢复网络全速)'
    echo '------------------------------------------------'
    
    # 使用安全的输入提示，由于部分电视盒子控制台无标准输入交互，采用默认降级或持续等待
    # 提示：在这里我们通过简单的逻辑提供菜单选择
    printf "请输入选项数字 (1 或 2): "
    read choice

    case "\$choice" in
        1)
            echo '-> 正在执行：限制上传速度...'
            mkdir -p /system/etc/init.d 2>/dev/null
            cat << 'EOF' > /system/etc/init.d/99limit_upload
#!/system/bin/sh
sleep 15
for dev in eth0 wlan0; do
    if [ -d "/sys/class/net/\$dev" ]; then
        tc qdisc del dev \$dev root 2>/dev/null
        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k
    fi
done
EOF
            chmod 755 /system/etc/init.d/99limit_upload 2>/dev/null

            if [ ! -f /system/etc/init.d/99limit_upload ]; then
                mkdir -p /data/adb/service.d
                cat << 'EOF' > /data/adb/service.d/limit_upload.sh
#!/system/bin/sh
sleep 15
for dev in eth0 wlan0; do
    if [ -d "/sys/class/net/\$dev" ]; then
        tc qdisc del dev \$dev root 2>/dev/null
        tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k
    fi
done
EOF
                chmod 755 /data/adb/service.d/limit_upload.sh
            fi

            for dev in eth0 wlan0; do
                if [ -d "/sys/class/net/\$dev" ]; then
                    tc qdisc del dev \$dev root 2>/dev/null
                    tc qdisc add dev \$dev root tbf rate 1mbit latency 50ms burst 128k
                    echo "已对网卡 \$dev 生效上行 1M 限速"
                fi
            done
            echo '✅ 上传限制已成功开启！'
            ;;
        2)
            echo '-> 正在执行：取消上传限制...'
            rm -f /system/etc/init.d/99limit_upload
            rm -f /data/adb/service.d/limit_upload.sh
            
            for dev in eth0 wlan0; do
                if [ -d "/sys/class/net/\$dev" ]; then
                    tc qdisc del dev \$dev root 2>/dev/null
                    echo "已清除网卡 \$dev 的限速规则，恢复全速"
                fi
            done
            echo '✅ 上传限制已解除，网络已恢复全速！'
            ;;
        *)
            echo '❌ 输入无效或未检测到输入，默认不作修改。'
            ;;
    esac
fi
""".trimIndent()

        tvLog.text = "正在初始化工具并获取 Root 权限...\n"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scriptFile = File(cacheDir, "interactive_task.sh")
                val cleanedScript = scriptContent.replace("\r\n", "\n")
                FileOutputStream(scriptFile).use { it.write(cleanedScript.toByteArray()) }
                scriptFile.setExecutable(true, false)

                tvLog.append("-> 脚本已就绪，正在启动...\n\n")

                // 注意：为了让用户能在黑框中看到输入提示并交互，这里以 sh 交互或流式管道挂载执行
                val process = ProcessBuilder("su")
                    .redirectErrorStream(true)
                    .start()

                val writer = process.outputStream.bufferedWriter()
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                // 向 su 进程输入执行命令的指令
                writer.write("sh ${scriptFile.absolutePath}\n")
                writer.flush()

                // 实时读取输出
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
