package com.example.farmerchatbot

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.content.Intent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var speakButton: Button
    private lateinit var responseTextView: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    private val geminiApiKey = "579b464db66ec23bdd000001c5f54dbc7989497d7be74c2a7c2ba0b3"
    private val openWeatherApiKey = "YOUR_OPENWEATHER_API_KEY" // <-- Replace this!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        speakButton = findViewById(R.id.speakButton)
        responseTextView = findViewById(R.id.responseTextView)

        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(applicationContext, "Speech error: $error", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    inputEditText.setText(matches[0])
                    processQuery(matches[0])
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        sendButton.setOnClickListener {
            val query = inputEditText.text.toString()
            processQuery(query)
        }

        speakButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            speechRecognizer.startListening(intent)
        }
    }

    private fun processQuery(query: String) {
        responseTextView.text = "Thinking..."

        // Simple detection
        when {
            query.contains("weather", ignoreCase = true) -> getWeather(query)
            query.contains("price", ignoreCase = true) || query.contains("market", ignoreCase = true) -> getMarketPrice(query)
            else -> getGeminiResponse(query)
        }
    }

    private fun getGeminiResponse(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$geminiApiKey")
                val postData = """
                    {
                        "contents": [{"parts": [{"text": "$query"}]}]
                    }
                    """.trimIndent()
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(postData.toByteArray())
                val response = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(response)
                val aiResponse = obj.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                runOnUiThread {
                    responseTextView.text = aiResponse
                    speakOut(aiResponse)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    responseTextView.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun getMarketPrice(query: String) {
        // For demo, just use Gemini API to predict price.
        getGeminiResponse("Predict market price for: $query")
    }

    private fun getWeather(query: String) {
        // Extract location from query, default to "Delhi"
        val location = query.split(" ").lastOrNull() ?: "Delhi"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://api.openweathermap.org/data/2.5/weather?q=$location&appid=$openWeatherApiKey&units=metric")
                val response = url.readText()
                val obj = JSONObject(response)
                val weather = obj.getJSONArray("weather").getJSONObject(0).getString("description")
                val temp = obj.getJSONObject("main").getDouble("temp")
                val answer = "Weather in $location: $weather, $temp °C"
                runOnUiThread {
                    responseTextView.text = answer
                    speakOut(answer)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    responseTextView.text = "Weather fetch error: ${e.message}"
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        }
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}