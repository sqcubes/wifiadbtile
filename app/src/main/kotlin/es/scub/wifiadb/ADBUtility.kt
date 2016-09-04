package es.scub.wifiadb

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader

internal object ADBUtility {
    const val port = 5555

    val isAdbStarted: Boolean get() = getProp("service.adb.tcp.port").contains("$port")

    private fun getProp(prop: String): String {
        try {

            val p = Runtime.getRuntime().exec("getprop " + prop)
            Thread.sleep(100)
            var output = ""
            val buf = BufferedReader(InputStreamReader(p.inputStream))
            buf.forEachLine { output += it }
            buf.close()

            return output


        } catch (ignored: Exception) {
        }

        return ""
    }

    @Throws(NoSuperUserException::class)
    private fun runAsRoot(cmds: Array<String>) {
        var seenRoot = false
        try {
            val p = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(p.outputStream)
            os.writeBytes("echo -ROOT-" + "\n")
            for (tmpCmd in cmds) {
                os.writeBytes(tmpCmd + "\n")
            }
            os.writeBytes("exit\n")
            os.flush()

            var output = ""
            val buf = BufferedReader(InputStreamReader(p.inputStream))
            buf.forEachLine { output += it }
            buf.close()

            if (output.contains("-ROOT-")) {
                seenRoot = true
            }
        } catch (ignore: IOException) {
        }

        if (!seenRoot) {
            throw NoSuperUserException()
        }
    }

    @Throws(NoSuperUserException::class)
    fun startAdb(force: Boolean) {
        if (!force && isAdbStarted) {
            return
        }
        //        String[] commands = {"setprop service.adb.tcp.port " + ADB_PORT};
        val commands = arrayOf("setprop service.adb.tcp.port " + port, "stop adbd", "start adbd")
        runAsRoot(commands)
    }

    @Throws(NoSuperUserException::class)
    fun stopAdb() {
        //        String[] commands = {"setprop service.adb.tcp.port -1"};
        val commands = arrayOf("setprop service.adb.tcp.port -1", "stop adbd", "start adbd")
        runAsRoot(commands)
    }

    fun hasSU(): Boolean {
        try {
            runAsRoot(arrayOf("exit"))
            return true
        } catch (e: NoSuperUserException) {
            return false
        }
    }

    fun getIPAddress(context: Context): String {
        val wm = context.getSystemService(WIFI_SERVICE) as WifiManager
        val ci = wm.connectionInfo
        val ipAddress = ci.ipAddress
        return String.format("%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff, ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
    }
}