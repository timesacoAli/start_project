package com.ma.example.viewModel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.ma.example.di.AppModule
import com.ma.example.di.CONTEXT_APP
import com.ma.example.di.DaggerServiceComponent
import com.ma.example.di.typeOfContext
import com.ma.example.model.DogBreed
import com.ma.example.model.DogDao
import com.ma.example.model.DogDatabase
import com.ma.example.model.DogsService
import com.ma.example.utils.NotificationHelper
import com.ma.example.utils.SharePreferencesHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import java.lang.NumberFormatException
import javax.inject.Inject

/**
 * Created by Muhammad Ali on 04-May-20.
 * Email muhammad.ali9385@gmail.com
 */
class ListViewModel(application: Application) : BaseViewModel(application) {


    @Inject
    @field:typeOfContext(CONTEXT_APP)
    lateinit var prefsHelper: SharePreferencesHelper
    private var refreshTime = 2 * 60 * 1000 * 1000 * 1000L

    @Inject
    lateinit var dogsService: DogsService
    private val disposable = CompositeDisposable()

    init {
        DaggerServiceComponent.builder()
            .appModule(AppModule(getApplication()))
            .build()
            .injectViewModel(this)
    }


    val dogs = MutableLiveData<List<DogBreed>>()
    val error = MutableLiveData<Boolean>()
    val loading = MutableLiveData<Boolean>()

    fun refresh() {

        checkCacheDuration()
        val updateTime = prefsHelper.getTime()
        if (updateTime != null && updateTime != 0L && System.nanoTime() - updateTime < refreshTime) {
            fetchFromDatabase()
        } else {
            fetchFromRemote()
        }
    }

    fun checkCacheDuration() {
        val cacheNumer = prefsHelper.getCachePreferences()
        try {
            val cacheNumberInt = cacheNumer?.toInt() ?: 5 * 60
            refreshTime = cacheNumberInt.times(1000 * 1000 * 1000L)
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
    }

    fun refreshBypassCache() {
        fetchFromRemote()
    }

    private fun fetchFromDatabase() {
        loading.value = true
        val dogDao = DogDatabase(getApplication()).dogDao()
        launch {
            val dogs = dogDao.getAllDogs()
            dogsRetrieve(dogs)
            Toast.makeText(getApplication(), "Retrived from Database", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchFromRemote() {
        loading.value = true
        disposable.add(
            dogsService.getDogs()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(object : DisposableSingleObserver<List<DogBreed>>() {
                    override fun onSuccess(t: List<DogBreed>) {
                        storeLocally(t)
                        Toast.makeText(getApplication(), "Retrived from Server", Toast.LENGTH_SHORT)
                            .show()
                        NotificationHelper(getApplication()).createNotification()
                    }

                    override fun onError(e: Throwable) {
                        error.value = true
                        loading.value = false
                        e.printStackTrace()
                    }

                })
        )
    }

    private fun dogsRetrieve(t: List<DogBreed>) {
        dogs.value = t
        error.value = false
        loading.value = false
    }

    private fun storeLocally(t: List<DogBreed>) {
        launch {

            val dao: DogDao = DogDatabase(getApplication()).dogDao()
//            dao.deleteAllDogs()
            val insertResult = dao.insertAll(*t.toTypedArray())
            fetchFromDatabase()
//            dogsRetrieve(fetchFromDatabase())
        }
        prefsHelper.updateTime(System.nanoTime())
    }

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }
}