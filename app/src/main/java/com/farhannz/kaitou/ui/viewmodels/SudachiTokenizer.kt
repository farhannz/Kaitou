package com.farhannz.kaitou.ui.viewmodels
//import android.app.Application
//import android.util.Log
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import com.farhannz.kaitou.data.models.*
//import com.farhannz.kaitou.helpers.SudachiManager
//import com.worksap.nlp.sudachi.Morpheme
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.launch
//
//
//// Represents the state of the dictionary popup
//sealed class DictionaryUiState {
//    object Idle : DictionaryUiState() // Nothing is selected
//    data class Loading(val ocrResult: OCRResult) : DictionaryUiState()
//    data class Success(val ocrResult: OCRResult, val morphemes: List<Morpheme>) : DictionaryUiState()
//    data class Error(val ocrResult: OCRResult, val message: String) : DictionaryUiState()
//}
//class SudachiTokenizer(application: Application) : AndroidViewModel(application) {
//    // This StateFlow now drives the popup's visibility and content
//    private val _dictUiState = MutableStateFlow<DictionaryUiState>(DictionaryUiState.Idle)
//    val dictUiState: StateFlow<DictionaryUiState> = _dictUiState
//
//    // Call this when a user clicks on an OCR bounding box
//    fun tokenizeOcrResult(result: OCRResult) {
//        // Prevent re-tokenizing the same selected word
//        if (_dictUiState.value is DictionaryUiState.Loading && (dictUiState.value as DictionaryUiState.Loading).ocrResult == result) return
//        if (_dictUiState.value is DictionaryUiState.Success && (dictUiState.value as DictionaryUiState.Success).ocrResult == result) return
//
//
//        viewModelScope.launch {
//            // Immediately show loading state at the correct position
//            _dictUiState.value = DictionaryUiState.Loading(result)
//            try {
//                val tokenizer = SudachiManager.getTokenizer(getApplication())
//                val morphemes = tokenizer.tokenize(result.word)
//                Log.i("Sudachi Tokenizer", "Word $result.word")
//                Log.i("Sudachi Tokenizer", "Morphemes $morphemes")
//                _dictUiState.value = DictionaryUiState.Success(result, morphemes)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                _dictUiState.value = DictionaryUiState.Error(result, "Failed to tokenize.")
//            }
//        }
//    }
//
//    // Call this to hide the popup
//    fun clearSelection() {
//        _dictUiState.value = DictionaryUiState.Idle
//    }
//}