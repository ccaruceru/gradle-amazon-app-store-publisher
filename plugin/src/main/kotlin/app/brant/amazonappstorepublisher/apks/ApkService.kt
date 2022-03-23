package app.brant.amazonappstorepublisher.apks

import app.brant.amazonappstorepublisher.PublishPlugin
import app.brant.amazonappstorepublisher.edits.Edit
import app.brant.amazonappstorepublisher.fetchtoken.Token
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*
import java.io.File

@kotlinx.serialization.Serializable
data class Apk(val versionCode: Int, val id: String, val name: String, var eTag: String = "")

@kotlinx.serialization.Serializable
data class Reason(val reason: String? = null, val details: List<String> = arrayListOf())

@kotlinx.serialization.Serializable
data class Device(val id: String, val name: String, var status: String, val reason: Reason?)

@kotlinx.serialization.Serializable
data class ApkTargeting(var amazonDevices: List<Device>, var nonAmazonDevices: List<Device>, val otherAndroidDevices: String?, @Transient var eTag: String = "")


class ApkService(val token: Token,
                 val version: String,
                 val applicationId: String) {
    interface GetApks {
        @GET("{version}/applications/{appId}/edits/{editId}/apks")
        fun getApksForEdit(
                @Header("Authorization") authorization: String,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String
        ): Call<List<Apk>>

        @GET("{version}/applications/{appId}/edits/{editId}/apks/{apkId}")
        fun getApkForEdit(
                @Header("Authorization") authorization: String,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String,
                @Path("apkId") apkId: String
        ): Call<Apk>
    }

    interface UploadApk {
        @Headers("Content-Type: application/vnd.android.package-archive")
        @POST("{version}/applications/{appId}/edits/{editId}/apks/upload")
        fun uploadApk(
                @Header("Authorization") authorization: String,
                @Header("filename") filename: String?,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String,
                @Body file: RequestBody
        ): Call<Apk>
    }

    interface UploadLargeApk {
        @Headers("Content-Type: application/vnd.android.package-archive")
        @POST("{version}/applications/{appId}/edits/{editId}/apks/large/upload")
        fun uploadLargeApk(
                @Header("Authorization") authorization: String,
                @Header("fileName") filename: String?,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String,
                @Body file: RequestBody
        ): Call<ResponseBody>
    }

    interface ApkTargetingService {
        @GET("{version}/applications/{appId}/edits/{editId}/apks/{apkId}/targeting")
        fun getApkTargeting(
                @Header("Authorization") authorization: String,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String,
                @Path("apkId") apkId: String
        ): Call<ApkTargeting>

        @PUT("{version}/applications/{appId}/edits/{editId}/apks/{apkId}/targeting")
        fun setApkTargeting(
                @Header("Authorization") authorization: String,
                @Header("If-Match") eTag: String,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String,
                @Path("apkId") apkId: String,
                @Body targeting: RequestBody
        ): Call<ResponseBody>
    }

    interface ReplaceApk {
        @Headers("Content-Type: application/vnd.android.package-archive")
        @PUT("{version}/applications/{appId}/edits/{editId}/apks/{apkId}/replace")
        fun replaceApk(
                @Header("Authorization") authorization: String,
                @Header("If-Match") eTag: String,
                @Header("filename") filename: String?,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String,
                @Path("apkId") apkId: String,
                @Body file: RequestBody
        ): Call<ResponseBody>
    }

    interface DeleteApk {
        @DELETE("{version}/applications/{appId}/edits/{editId}/apks/{apkId}")
        fun deleteApk(
                @Header("Authorization") authorization: String,
                @Header("If-Match") eTag: String,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String,
                @Path("apkId") apkId: String
        ): Call<ResponseBody>
    }

    interface AttachApk {
        @POST("{version}/applications/{appId}/edits/{editId}/apks/attach")
        fun attachApkToEdit(
                @Header("Authorization") authorization: String,
                @Path("version") version: String,
                @Path("appId") applicationId: String,
                @Path("editId") editId: String,
                @Body apkAsset: RequestBody
        ): Call<Apk>
    }


    fun getApk(editId: String, apkId: String): Apk {
        val getApksService = PublishPlugin.retrofit
                .create(ApkService.GetApks::class.java)
        val response: Response<Apk> = getApksService.getApkForEdit(
                "Bearer ${token.access_token}",
                version,
                applicationId,
                editId,
                apkId
        ).execute()
        return responseToApk(response)
    }

    fun getApks(editId: String): List<Apk> {
        val getApksService = PublishPlugin.retrofit
                .create(ApkService.GetApks::class.java)
        val response: Response<List<Apk>> = getApksService.getApksForEdit(
                "Bearer ${token.access_token}",
                version,
                applicationId,
                editId
        ).execute()
        return extractApkData(response)
    }

    fun deleteApk(editId: String, apkId: String): Boolean {
        val apk = getApk(editId, apkId)
        val deleteApkService = PublishPlugin.retrofit
                .create(ApkService.DeleteApk::class.java)
        val response: Response<ResponseBody> =
                deleteApkService.deleteApk(
                        "Bearer ${token.access_token}",
                        apk.eTag,
                        version,
                        applicationId,
                        editId,
                        apkId
                ).execute()
        return response.isSuccessful
    }

    fun replaceApk(editId: String, apkId: String, apkFile: File, filename: String?): Boolean {
        val apk = getApk(editId, apkId)
        val replaceApkService = PublishPlugin.retrofit
                .create(ApkService.ReplaceApk::class.java)
        val requestBody = RequestBody.create(
                MediaType.parse("application/vnd.android.package-archive"),
                apkFile.readBytes()
        )

        val response: Response<ResponseBody> =
                replaceApkService.replaceApk(
                        "Bearer ${token.access_token}",
                        apk.eTag,
                        filename,
                        version,
                        applicationId,
                        editId,
                        apkId,
                        requestBody
                ).execute()
        return response.isSuccessful
    }

    fun uploadApk(editId: String, apk: File, filename: String?): Apk? {
        val requestBody = RequestBody.create(
                MediaType.parse("application/octet-stream"),
                apk.readBytes()
        )

        if (apk.length() >= 300 * 1024 * 1024) { // 300MB
                val uploadLargeApkService = PublishPlugin.retrofit
                        .create(ApkService.UploadLargeApk::class.java)

                val response: Response<ResponseBody> =
                        uploadLargeApkService.uploadLargeApk(
                                "Bearer ${token.access_token}",
                                filename,
                                version,
                                applicationId,
                                editId,
                                requestBody
                        ).execute()

                if (!response.isSuccessful) {
                        return null
                }
                return attachApkToEdit(editId, response.body()!!.string())
        }
        else {
                val uploadApkService = PublishPlugin.retrofit
                        .create(ApkService.UploadApk::class.java)

                val response: Response<Apk> =
                        uploadApkService.uploadApk(
                                "Bearer ${token.access_token}",
                                filename,
                                version,
                                applicationId,
                                editId,
                                requestBody
                        ).execute()

                if(!response.isSuccessful) {
                        return null
                }
                return responseToApk(response)
        }
    }

    fun getApkTargeting(editId: String, apkId: String): ApkTargeting? {
        val apkTargetingService = PublishPlugin.retrofit
                .create(ApkService.ApkTargetingService::class.java)
        val response: Response<ApkTargeting> = apkTargetingService.getApkTargeting(
                "Bearer ${token.access_token}",
                version,
                applicationId,
                editId,
                apkId
        ).execute()
        if (!response.isSuccessful) {
                return null
        }
        val apkTargeting = response.body()!!
        val eTag = response.headers().get("ETag")!!
        apkTargeting.eTag = eTag
        return apkTargeting
    }

    fun setApkTargeting(editId: String, apkId: String, apkTargeting: ApkTargeting): Boolean {
        val apkTargetingService = PublishPlugin.retrofit
                .create(ApkService.ApkTargetingService::class.java)
        val requestBody = RequestBody.create(
                MediaType.parse("application/json"),
                Json.stringify(ApkTargeting.serializer(), apkTargeting)
        )

        val response: Response<ResponseBody> = apkTargetingService.setApkTargeting(
                "Bearer ${token.access_token}",
                apkTargeting.eTag,
                version,
                applicationId,
                editId,
                apkId,
                requestBody
        ).execute()

        return response.isSuccessful
    }

    fun attachApkToEdit(editId: String, fileIdJson: String): Apk? {
        val attachApkService = PublishPlugin.retrofit
                .create(ApkService.AttachApk::class.java)
        val requestBody = RequestBody.create(
                MediaType.parse("application/json"),
                fileIdJson
        ) 

        // TODO: remove me
        println("sleeping 1 min...")
        Thread.sleep(60 * 1000L)

        val response: Response<Apk> =
                attachApkService.attachApkToEdit(
                        "Bearer ${token.access_token}",
                        version,
                        applicationId,
                        editId,
                        requestBody
                ).execute()
        if (!response.isSuccessful) {
                return null
        }
        return responseToApk(response)
    }


    private fun extractApkData(response: Response<List<Apk>>): List<Apk> {
        val list = response.body()
        return list!!.map {
            Apk(it.versionCode, it.id, it.name)
        }
    }

    private fun responseToApk(response: Response<Apk>): Apk {
        val apk = response.body()!!
        apk.eTag = response.headers().get("ETag") ?: ""
        return apk
    }
}