package com.example.dictionaryapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.dictionaryapp.BuildConfig
import com.example.dictionaryapp.databinding.FragmentHomeBinding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Needs a job to handle the coroutine cancellation to prevent leaks (https://stackoverflow.com/a/59609020)
    val job = Job()
    val uiScope = CoroutineScope(Dispatchers.Main + job)

    // Coroutine exception handler (https://stackoverflow.com/a/71004286)
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        // Creates a ViewModel object
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        // Inflates the layout for this fragment
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome

        // Sets the text of textView with data from homeViewModel
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchView: SearchView = binding.searchView

        // Instantiates the client by creating the HttpClient class instance
        val client = HttpClient() {
            // Configures the client
            defaultRequest {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "owlbot.info"
                    path("api/v4/dictionary/")
                }

                val apiKey = BuildConfig.API_KEY
                val headerValue = "Token " + apiKey
                header("Authorization", headerValue)
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {

                // Adds a coroutine exception handler in launch code (able to log errors to Logcat)
                uiScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                    // asyncOperation

                    // Makes an HTTP get request
                    // With the above client configuration, the resulting URL will be: https://owlbot.info/api/v4/dictionary/{query}
                    // {query} is an english word such that animal
                    val response: HttpResponse =
                        client.get(
                            URLEncoder.encode( //convert all spaces in a string "query" to "+"
                                query,
                                "UTF-8"
                            )
                        )

                    withContext(Dispatchers.Main) {
                        // ui operation
                        val textView: TextView = binding.textHome
                        textView.text = response.status.toString()
                    }
                }
                return false
            }

            override fun onQueryTextChange(p0: String?): Boolean {
                return false
            }
        })
    }

/* An alternative solution to make HTTP requests
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchView: SearchView = binding.searchView

        // Instantiates the client by creating the HttpClient class instance
        val client = HttpClient()

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String?): Boolean {

                // Adds a coroutine exception handler in launch code (able to log errors to Logcat)
                uiScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                    // asyncOperation

                    val stringURL = ("https://owlbot.info/api/v4/dictionary/"
                            + URLEncoder.encode( //convert all spaces in a string "query" to "+"
                        query,
                        "UTF-8"
                    ))

                    // Makes an HTTP get request
                    val response: HttpResponse =
                        client.get(stringURL) {
                            headers {
                                append(
                                    HttpHeaders.Authorization,
                                    "Token **********************************"
                                )
                            }
                        }

                    withContext(Dispatchers.Main) {
                        // ui operation
                        val textView: TextView = binding.textHome
                        textView.text = response.status.toString()
                    }
                }
                return false
            }

            override fun onQueryTextChange(p0: String?): Boolean {
                return false
            }
        })
    }
*/

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}