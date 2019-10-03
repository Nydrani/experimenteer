package xyz.velvetmilk.testingtool

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.threeten.bp.Instant
import timber.log.Timber
import xyz.velvetmilk.testingtool.models.DeviceInfoCore
import java.io.IOException
import kotlin.coroutines.CoroutineContext

class JSONActivity : AppCompatActivity(), CoroutineScope {

    private class InstantTypeAdapter : TypeAdapter<Instant>() {
        @Throws(IOException::class)
        override fun read(`in`: JsonReader): Instant? {
            return Instant.ofEpochMilli(`in`.nextLong())
        }

        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: Instant?) {
            out.value(value?.toEpochMilli())
        }
    }

    companion object {
        private val TAG = JSONActivity::class.simpleName

        fun buildIntent(context: Context): Intent {
            return Intent(context, JSONActivity::class.java)
        }
    }

    private lateinit var disposer: CompositeDisposable
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_json)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        job = Job()
        disposer = CompositeDisposable()

        fab.setOnClickListener {
            val gson = GsonBuilder()
                .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
                .create()
            val res = gson.toJson(DeviceInfoCore.generateDeviceInfo(contentResolver, packageManager, packageName))
            Timber.d(res)
            base_view.text = res
        }

        fab2.setOnClickListener {
            // random test
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        job.cancel()
        disposer.clear()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }
}