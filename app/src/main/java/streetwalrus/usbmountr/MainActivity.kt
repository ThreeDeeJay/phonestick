package streetwalrus.usbmountr

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import eu.chainfire.libsuperuser.Shell

class MainActivity : Activity() {
    private val TAG = "MainActivity"

    private var mPrefs: HostPreferenceFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mPrefs = fragmentManager.findFragmentById(R.id.prefs) as HostPreferenceFragment
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_licenses -> {
                val intent = Intent(this, LicenseActivity::class.java)
                startActivity(intent)
            }
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        val appContext = applicationContext as UsbMountrApplication
        appContext.onActivityResult(requestCode, resultCode, resultData)
    }

    @Suppress("unused")
    fun onServeClicked(@Suppress("UNUSED_PARAMETER") v: View) {
        // Escape the file name to avoid bugs in the shell
        // Could use some finer filters but who cares
        val file = "(.)".toRegex().replace(
                mPrefs!!.preferenceManager.sharedPreferences
                        .getString(mPrefs!!.SOURCE_KEY, ""),
                "\\\\$1")

        val ro = if (mPrefs!!.preferenceManager.sharedPreferences
                .getBoolean(mPrefs!!.RO_KEY, true)) "1" else "0"

        UsbScript().execute(file, ro, "1")
    }

    @Suppress("unused")
    fun onDisableClicked(@Suppress("UNUSED_PARAMETER") v: View) {
        UsbScript().execute("", "1", "0")
    }

    inner class UsbScript : AsyncTask<String, Void, Int>() {
        override fun doInBackground(vararg params: String): Int {
            val usb = "/config/usb_gadget/g1"
            val usb_fun = "$usb/functions/mass_storage.usb0"
            val usb_lun = "$usb_fun/mass_storage.usb0/lun.0"
            val usb_cfg = "$usb/configs/b.1"
            val file = params[0]
            val ro = params[1]
            val enable = params[2]

            if (!(Shell.SU.run(arrayOf(
                    // Remember current UDC and disable USB
                    "cat $usb/UDC > /data/local/tmp/udc.bak",
                    "echo 'none' > $usb/UDC",

                    // Disable and remove any existing mass storage gadget(s)
                    "if [ -e $usb_cfg/mass_storage.usb0 ]; then rm -f $usb_cfg/mass_storage.usb0; fi",
                    "if [ -e $usb_fun/mass_storage.usb0 ]; then rmdir $usb_fun/mass_storage.usb0; fi",

                    // Are we enabling it?
                    "[[ 1 == $enable ]] && (",
                        // If so, create and configure mass storage gadget
                        "mkdir $usb_fun",
                        "echo 0 > $usb_lun/cdrom",
                        "echo $file > $usb_lun/file",
                        "echo 'USBMountr' > $usb_lun/inquiry_string",
                        "echo 1 > $usb_lun/nofua",
                        "echo $ro > $usb_lun/ro",

                        // Enable the gadget
                        "cd $usb_cfg",
                        "ln -s ../../functions/mass_storage.usb0",
                        "cd -",
                    ")",

                    // Restore UDC (re-enables USB) and remove temp file
                    "cat /data/local/tmp/udc.bak > $usb/UDC",
                    "rm /data/local/tmp/udc.bak",

                    // And we're done!
                    "echo success"
            ))?.isEmpty() ?: true)) {
                if (enable != "0") {
                    return R.string.host_success
                } else {
                    return R.string.host_disable_success
                }
            } else {
                return R.string.host_noroot
            }
        }

        override fun onPostExecute(result: Int) {
            Toast.makeText(applicationContext, getString(result), Toast.LENGTH_SHORT).show()
        }
    }
}
