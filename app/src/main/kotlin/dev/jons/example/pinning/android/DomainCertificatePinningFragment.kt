package dev.jons.example.pinning.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dev.jons.example.pinning.android.GatherCertificateHashesIntentService.Companion.getCertistApiHashesForDomain
import dev.jons.example.pinning.android.GatherCertificateHashesIntentService.Companion.getLocalSocketHashesForDomain
import java.util.*

class DomainCertificatePinningFragment : Fragment() {
    private val localHashes: ArrayList<String?> = ArrayList()
    private val certistHashes: ArrayList<String?> = ArrayList()
    private var localSocketResultsAdapter: ArrayAdapter<String?>? = null
    private var certIstResultsAdapter: ArrayAdapter<String?>? = null
    private var rootView: View? = null
    private var editText: EditText? = null
    private var doHashesMatch: TextView? = null
    private val localResultsFilter = IntentFilter(
            GatherCertificateHashesIntentService.ACTION_RESULT_LOCAL_SOCKET_HASHES)
    private val certIstResultsFilter = IntentFilter(
            GatherCertificateHashesIntentService.ACTION_RESULT_CERTIST_HASHES)

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        this.rootView = inflater.inflate(R.layout.fragment_certificate_pinning, container, false)
        localSocketResultsAdapter = ArrayAdapter(inflater.context,
                R.layout.expected_hash, R.id.list_view_item_hash, localHashes)
        certIstResultsAdapter = ArrayAdapter(inflater.context,
                R.layout.expected_hash, R.id.list_view_item_hash, certistHashes)
        this.editText = this.rootView?.findViewById(R.id.edit_text_first)
        this.editText?.setOnKeyListener { _: View?, keyCode: Int, event: KeyEvent ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_ENTER) {
                gatherCertificates()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
        this.editText?.doOnTextChanged { text, start, before, count ->

        }
        doHashesMatch = this.rootView?.findViewById(R.id.do_certificates_match_label)
        (this.rootView?.findViewById<View>(R.id.local_socket_results) as ListView).adapter = localSocketResultsAdapter
        (this.rootView?.findViewById<View>(R.id.certist_results) as ListView).adapter = certIstResultsAdapter
        this.rootView?.findViewById<View>(R.id.button_first)?.setOnClickListener { _: View? -> gatherCertificates() }
        return rootView
    }

    private fun gatherCertificates() {
        val manager = LocalBroadcastManager.getInstance(rootView!!.context)
        manager.registerReceiver(getResultsReceiver(manager,
                localSocketResultsAdapter, R.id.local_socket_results_label), localResultsFilter)
        manager.registerReceiver(getResultsReceiver(manager,
                certIstResultsAdapter, R.id.certist_results_label), certIstResultsFilter)
        val domain = editText!!.text.toString()
        getCertistApiHashesForDomain(rootView!!.context, domain)
        getLocalSocketHashesForDomain(rootView!!.context, domain)
    }

    private fun getResultsReceiver(
            manager: LocalBroadcastManager,
            adapter: ArrayAdapter<String?>?,
            label: Int): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                rootView!!.findViewById<View>(label).visibility = View.VISIBLE
                manager.unregisterReceiver(this)
                adapter!!.clear()
                adapter.addAll(*intent.extras?.getStringArray(GatherCertificateHashesIntentService.RESULT_SHA256_HASHES))
                adapter.notifyDataSetChanged()
                allFound()
            }
        }
    }

    private fun grabHashesFromAdapter(adapter: ArrayAdapter<String?>?): List<String?> {
        val l: MutableList<String?> = ArrayList()
        (0 until adapter!!.count).forEach { i -> l += adapter.getItem(i) }
        return l
    }

    private fun allFound() {
        doHashesMatch!!.visibility = View.VISIBLE
        val certist = grabHashesFromAdapter(certIstResultsAdapter)
        val local = grabHashesFromAdapter(localSocketResultsAdapter)
        val matchPair = Pair(resources.getColor(android.R.color.holo_green_dark, null),
                R.string.certificate_hashes_match_label)
        val noMatchPair = Pair(Color.RED, R.string.certificate_hashes_donot_match_label)
        doHashesMatch!!.setTextColor(if (certist == local) matchPair.first else noMatchPair.first)
        doHashesMatch!!.setText(if (certist == local) matchPair.second else noMatchPair.second)
    }
}
