package com.example.nfcapp

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.nfcapp.ui.theme.NFCAppTheme
import java.util.Locale
import kotlin.text.Charsets

class MainActivity : ComponentActivity() {

    private var nfcContent by mutableStateOf("Aproxime um cartão NFC")
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            nfcContent = "NFC não é suportado neste dispositivo."
        } else if (!nfcAdapter!!.isEnabled) {
            nfcContent = "NFC está desativado. Por favor, ative-o nas definições."
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }

        setContent {
            NFCAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NfcReaderScreen(
                        nfcContent = nfcContent,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {
            // IMPORTANTE: FLAG_MUTABLE é necessário para que o sistema possa adicionar os dados da tag ao Intent
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE
            )

            // Filtro "apanha-tudo" para garantir que detetamos qualquer tag
            val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            val intentFilters = arrayOf(tagFilter)

            // techLists null significa que aceitamos qualquer tecnologia se usarmos ACTION_TAG_DISCOVERED
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        // Aceitamos qualquer ação de NFC
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action || 
            NfcAdapter.ACTION_TECH_DISCOVERED == action || 
            NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            
            Toast.makeText(this, "Cartão NFC detetado!", Toast.LENGTH_SHORT).show()

            var ndefContentFound = false
            val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (rawMessages != null) {
                val ndefMessages = rawMessages.map { it as NdefMessage }
                for (message in ndefMessages) {
                    for (record in message.records) {
                        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                            try {
                                val langCodeLen = record.payload[0].toInt() and 0x3F
                                val textEncoding = if ((record.payload[0].toInt() and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
                                if (record.payload.size > langCodeLen + 1) {
                                    nfcContent = String(record.payload, langCodeLen + 1, record.payload.size - langCodeLen - 1, textEncoding)
                                    ndefContentFound = true
                                    break
                                }
                            } catch (e: Exception) {
                                // Ignora erros de parsing
                            }
                        }
                    }
                    if (ndefContentFound) break
                }
            }

            if (!ndefContentFound) {
                val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                if (tag != null) {
                    val tagId = tag.id.joinToString(":") { String.format(Locale.US, "%02X", it) }
                    nfcContent = "ID da Tag: $tagId"
                } else {
                    nfcContent = "Tag detetada, mas sem dados legíveis."
                }
            }
        }
    }
}

@Composable
fun NfcReaderScreen(nfcContent: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = nfcContent,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NfcReaderScreenPreview() {
    NFCAppTheme {
        NfcReaderScreen("Aproxime um cartão NFC")
    }
}
