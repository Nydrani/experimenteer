package xyz.velvetmilk.testingtool

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_crypto.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import kotlin.coroutines.CoroutineContext


class CryptoActivity : AppCompatActivity(), CoroutineScope {

    companion object {
        private val TAG = CryptoActivity::class.java.simpleName
        private const val RSA_SIGNATURE_ALGORITHM = "SHA256withRSA/PSS"
        private const val RSA_KEY_ALIAS = "RSAbabey"
        private const val SERVER_KEY_ALIAS = "serverCert"
        private const val KEYSTORE_TYPE = "AndroidKeyStore"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"

        private const val SERVER_URL = "http://192.168.105.14:3000/"

        private const val SERVER_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n" +
                "MIICpDCCAYwCCQDnT3CKLvm27DANBgkqhkiG9w0BAQsFADAUMRIwEAYDVQQDDAls\n" +
                "b2NhbGhvc3QwHhcNMTkwNTIwMDQxNDEzWhcNMjAwNTE5MDQxNDEzWjAUMRIwEAYD\n" +
                "VQQDDAlsb2NhbGhvc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDT\n" +
                "YiBrsX2ZkicGhqasFwi9RcsTSyHKWkJ/loGWaFFczdcJI3CnPIA9LPPoYaR7Lo9B\n" +
                "wzp8AaIgyqBaqwgqXiIxe8nPHMSg05S6ytNLf/TlscNgZhjvWjyFmef0tWhDtWNB\n" +
                "i4pSJYWsHybYqmhntc2UPXuklJ1bsf5QV0wgJ/uOURkD/K9KRHMTGX038m0Skhy3\n" +
                "s4nX+d9mo+wLpLGclyU9i2k4XrPYSfCyhkd1slTqWDhrs51uvoBDLbVDgvnYPUgd\n" +
                "aWaioybru0vSgZi+xrEli8/XQM9EIM7jseFyQUrkulK5IiNJVnKg0IsD4DAkwt6b\n" +
                "DVdsTC39q4eMO4vqYChFAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAE89kjV2cgxY\n" +
                "1In/Bbf7pptGaXbn+twZDB77ooujxQrWmmUEWTdT4mfabPdulUHZtyYq2tY2P5+K\n" +
                "MKikN0npeYc5OlxBH7brPXB8Ekd1Ppx7amiAv40NcSfyXC6nkqKtJzspJzDaOUA/\n" +
                "oRgUAx/KR03WkbViQqz5z2X4W9Nh11Yi6saNGj8cJtGYWsM3TXPvENYJ5I6Q60q6\n" +
                "OXaoxF98CIOvOydas/EViV3J/0dRVCh5QD3XWcl3dssMSO29D81BLOcwrqxnJrMW\n" +
                "dqCH0OSppNu5RSzmr9KHHDE73V0ySz5CvG9bTD73Eh9hk+Na7CJiJQZdJ69PzeVz\n" +
                "z5BbwV0Yugk=\n" +
                "-----END CERTIFICATE-----"

        fun buildIntent(context: Context): Intent {
            return Intent(context, CryptoActivity::class.java)
        }
    }

    interface NetworkService {
        data class GetSignatureResponse(val signature: String)

        @POST("get-signature")
        fun getSignatureAsync(): Deferred<GetSignatureResponse>
    }

    private lateinit var disposer: CompositeDisposable
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job


    private lateinit var service: NetworkService

    private lateinit var store: KeyStore
    private lateinit var keyPairGenerator: KeyPairGenerator
    private lateinit var certificateFactory: CertificateFactory

    private lateinit var genJob: Job
    private var signature: ByteArray = byteArrayOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crypto)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        job = Job()
        disposer = CompositeDisposable()

        val retrofit = Retrofit.Builder()
            .baseUrl(SERVER_URL)
            .client(OkHttpClient())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
        service = retrofit.create(NetworkService::class.java)

        store = KeyStore.getInstance(KEYSTORE_TYPE).apply { this.load(null) }
        certificateFactory = CertificateFactory.getInstance("X.509")
        keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE_PROVIDER)


        // generate a certificate on background thread
        launch(Dispatchers.IO) {
            val cert = certificateFactory.generateCertificate(ByteArrayInputStream(SERVER_CERTIFICATE.toByteArray(Charsets.UTF_8)))
            store.setCertificateEntry(SERVER_KEY_ALIAS, cert)
        }

        // generate a keypair on background thread
        launch(Dispatchers.IO) {
            val rsaSpec = KeyGenParameterSpec.Builder(RSA_KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setAttestationChallenge("yeet".fromHexStringUTF8())
                .setKeySize(2048)
                .build()
            keyPairGenerator.initialize(rsaSpec)
            val pair = keyPairGenerator.genKeyPair()
            launch(Dispatchers.Main) {
                crypto_view.text = pair.public.encoded.toHexStringUTF8()
            }
        }


        fab.setOnClickListener {
            val entry = store.getEntry(RSA_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            val privateKey: PrivateKey = entry.privateKey

            if (::genJob.isInitialized) {
                genJob.cancel()
            }
            genJob = launch(Dispatchers.IO) {
                // create signature
                val signInstance = Signature.getInstance(RSA_SIGNATURE_ALGORITHM)
                signInstance.initSign(privateKey)
                signInstance.update(42)

                signature = signInstance.sign()
                launch(Dispatchers.Main) {
                    crypto_view.text = signature.toBase64()
                }
            }
        }

        fab2.setOnClickListener {
            val entry = store.getEntry(RSA_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            val certificate: Certificate = entry.certificate

            if (::genJob.isInitialized) {
                genJob.cancel()
            }
            genJob = launch(Dispatchers.IO) {
                // verify signature
                val verifyInstance = Signature.getInstance(RSA_SIGNATURE_ALGORITHM)
                verifyInstance.initVerify(certificate)
                verifyInstance.update(42)

                val res = verifyInstance.verify(signature)
                launch(Dispatchers.Main) {
                    crypto_view.text = res.toString()
                }
            }
        }

        fab3.setOnClickListener {
            val entry = store.getEntry(SERVER_KEY_ALIAS, null) as KeyStore.TrustedCertificateEntry
            val certificate: Certificate = entry.trustedCertificate

            if (::genJob.isInitialized) {
                genJob.cancel()
            }
            genJob = launch(Dispatchers.IO) {
                try {
                    // verify signature from server
                    val sig = service.getSignatureAsync().await().signature.fromBase64()

                    // verify signature
                    val verifyInstance = Signature.getInstance(RSA_SIGNATURE_ALGORITHM)
                    verifyInstance.initVerify(certificate)
                    verifyInstance.update(42)

                    val res = verifyInstance.verify(sig)
                    launch(Dispatchers.Main) {
                        crypto_view.text = res.toString()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        fab4.setOnClickListener {
            val builder = StringBuilder()
            for (item in store.aliases()) {
                builder.appendln(item)
            }
            crypto_view.text = builder.toString()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        job.cancel()
        disposer.clear()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }
}
