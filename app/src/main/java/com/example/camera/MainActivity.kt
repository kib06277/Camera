package com.example.camera
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 請求相機權限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // 設置拍照按鈕的監聽器
        camera_capture_button.setOnClickListener{ takePhoto() }
        outputDirectory = getOutputDirectory()
        Log.i("EE", "outputDirectory = " + outputDirectory);
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //檢查權限是否正確
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            //如果已經授予權限，系統則會調用 startCamera()。
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                //如果未授予權限，系統則會跳出提示框
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        //此實例用於將相機的生命週期綁定到生命週期所有者。由於CameraX 具有生命週期感知能力，所以這樣可以省去打開和關閉相機的任務。
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)
        //val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        //我們將稍後為其填入數值。這將返回在主線程上運行的 cameraProviderFutureRunnableContextCompat.getMainExecutor()Executor
        cameraProviderFuture.addListener(Runnable {

            // 用於將相機的生命週期綁定到生命週期所有者
            // 此類用於將相機的生命週期綁定到應用進程內的 RunnableProcessCameraProviderLifecycleOwner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 預覽
            //初始化 Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // 默認選擇後置攝像頭
            // 創建 CameraSelectorDEFAULT_BACK_CAMERA
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageCapture = ImageCapture.Builder().build()

            //確保任何內容都未綁定到您的 trycameraProvidercameraSelectorcameraProvider
            try {
                // 在重新綁定之前取消綁定用例
                cameraProvider.unbindAll()

                // 將用例綁定到相機
                //  LifecycleOwner 與 activity/fragment 生命週期綁定
                // CameraSelector 相機選擇器，用於選擇前後置相機
                // Preview 相機預覽，通過preview.setSurfaceProvider() 與 PreviewView 綁定實現預覽
                // ImageCapture 拍照，拍照由這個對象觸發和監聽
                // ImageAnalysis 數據解析，實時監聽相機圖像數據
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        Log.i("EE", "enter takephoto")

        // 獲取可修改圖像捕獲用例的穩定參考
        // 獲取對 如果用例為 null，則退出函數。如果您在設置拍攝圖像之前點按拍照按鈕，則這將為 null。如果沒有 ImageCapturereturnnull
        val imageCapture = imageCapture ?: return

        Log.i("EE", "imageCapture = " + imageCapture);

        // 創建帶時間戳的輸出文件以保存圖像
        // 創建一個容納圖像的文件。添加時間戳，以避免文件名重複。
        val photoFile = File(outputDirectory, SimpleDateFormat(FILENAME_FORMAT, Locale.TAIWAN).format(System.currentTimeMillis()) + ".jpg")

        Log.i("EE", "photoFile = " + photoFile);

        // 創建包含文件 + 元數據的輸出選項對象
        // 創建 您可以在此對像中指定有關輸出方式的設置。如果您希望將輸出內容保存在剛創建的文件中，則添加您的 OutputFileOptionsphotoFile
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        Log.i("EE", "outputOptions = " + outputOptions);

        // 設置圖像捕獲監聽器，在照片有後觸發
        // 被佔用
        // 傳入執行程序 接下來，您將填寫回調。imageCapture takePicture() outputOptions
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {

                //在圖像拍攝失敗或圖像拍攝結果保存失敗的情況下，添加一個錯誤示例，以記錄失敗情況。
                override fun onError(exc: ImageCaptureException) {
                    Log.i("EE", "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(baseContext, "截圖失敗", Toast.LENGTH_SHORT).show()
                }

                //如果拍攝未失敗，則表示拍照成功！將照片保存到您先前創建的文件中，顯示一個消息框以告知用戶操作成功，然後輸出日誌語句。
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Log.i("EE", "savedUri = " + savedUri)

                    val msg = "Photo capture succeeded:" + savedUri
                    Log.i("EE", "msg = " + msg)
                    Toast.makeText(baseContext, msg , Toast.LENGTH_SHORT).show()
                }
        })
    }
}