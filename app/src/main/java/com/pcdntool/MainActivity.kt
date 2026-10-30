package com.pcdntool

import android.os.Bundle
import android.widget.Button
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
        val btnRun = findViewById<Button>(R.id.btnRun)
        btnRun.setOnClickListener {
            runScript()
        }
    }

    private fun runScript() {
        val script = "\n" +
            "setenforce 0\n" +
            "echo === 开始删除PCDN守护进程并限制上传1M ===\n" +
            "killall -9 pcdn_node\n" +
            "killall -9 pcdn_daemon\n" +
            "am force-stop com.xxx.pcdn\n" +
            "pm uninstall com.xxx.pcdn\n" +
            "rm -rf /data/pcdn\n" +
            "rm -rf /data/local/tmp/pcdn*\n" +
            "rm -f /etc/init.d/S99pcdn\n" +
            "rm -f /vendor/etc/init/pcdn.rc\n" +
            "# 永久限速1mbit上传 eth0，按需改网卡\n" +
            "tc qdisc del dev eth0 root 2>/dev/null\n" +
            "tc qdisc add dev eth0 root tbf rate 1mbit latency 50ms burst 128k\n" +
            "echo === 执行完成 ===\n"

        tvLog.text = ""
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
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
