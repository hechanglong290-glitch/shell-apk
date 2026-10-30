package com.shellrunner;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this, "正在后台执行清理与限速脚本...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                InputStream is = getAssets().open("run.sh");
                File scriptFile = new File(getCacheDir(), "run.sh");
                FileOutputStream fos = new FileOutputStream(scriptFile);
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
                fos.close();
                is.close();
                scriptFile.setExecutable(true, false);

                // 使用 su 提权以 Root 身份执行脚本
                Process proc = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(proc.getOutputStream());
                os.writeBytes("sh " + scriptFile.getAbsolutePath() + "\n");
                os.writeBytes("exit\n");
                os.flush();
                proc.waitFor();
                Log.d("SHELL", "Root 脚本执行完毕");
            } catch (Exception e) {
                e.printStackTrace();
            }
            runOnUiThread(this::finish);
        }).start();
    }
}
