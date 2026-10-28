package br.com.controller.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cliente HTTP para o endpoint mdmAgent do Controller. */
object ApiClient {
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun post(payload: JSONObject): JSONObject {
        val req = Request.Builder()
            .url(Constants.AGENT_ENDPOINT)
            .header("X-App-Id", Constants.APP_ID)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            return if (body.isNotEmpty()) JSONObject(body)
            else JSONObject().put("success", false).put("error", "Resposta vazia (${resp.code})")
        }
    }

    /** Troca o codigo de registro pelo segredo do aparelho. */
    fun enroll(code: String, modelo: String, soVersao: String): JSONObject {
        return post(
            JSONObject()
                .put("action", "enroll")
                .put("code", code)
                .put("modelo", modelo)
                .put("so_versao", soVersao)
        )
    }

    /** Envia telemetria e recebe config + comandos pendentes. */
    fun report(
        enrollmentId: String,
        secret: String,
        lat: Double?,
        lon: Double?,
        precisao: Float?,
        bateria: Int?,
        rede: String?,
        carregando: Boolean?,
        soVersao: String
    ): JSONObject {
        val p = JSONObject()
            .put("action", "report")
            .put("enrollment_id", enrollmentId)
            .put("device_secret", secret)
            .put("so_versao", soVersao)
        if (lat != null) p.put("latitude", lat)
        if (lon != null) p.put("longitude", lon)
        if (precisao != null) p.put("precisao_metros", precisao.toDouble())
        if (bateria != null) p.put("nivel_bateria", bateria)
        if (rede != null) p.put("rede_tipo", rede)
        if (carregando != null) p.put("status_carregamento", carregando)
        return post(p)
    }

    fun pollCommands(enrollmentId: String, secret: String): JSONObject {
        return post(
            JSONObject()
                .put("action", "pollCommands")
                .put("enrollment_id", enrollmentId)
                .put("device_secret", secret)
        )
    }

    fun ackCommand(enrollmentId: String, secret: String, comandoId: String, status: String, resultado: String): JSONObject {
        return post(
            JSONObject()
                .put("action", "ackCommand")
                .put("enrollment_id", enrollmentId)
                .put("device_secret", secret)
                .put("comando_id", comandoId)
                .put("status", status)
                .put("resultado", resultado)
        )
    }

    fun comandosFrom(resp: JSONObject): JSONArray = resp.optJSONArray("comandos") ?: JSONArray()

    /** Termo pendente vindo no report/poll (ou null). */
    fun termoFrom(resp: JSONObject): JSONObject? = resp.optJSONObject("termo_pendente")

    /** Busca o colaborador no cadastro (ADL) pelo CPF. */
    fun buscarColaborador(enrollmentId: String, secret: String, cpf: String): JSONObject {
        return post(
            JSONObject()
                .put("action", "buscarColaborador")
                .put("enrollment_id", enrollmentId)
                .put("device_secret", secret)
                .put("cpf", cpf)
        )
    }

    /** Envia a assinatura do termo (feita no app, sem login). */
    fun assinarTermo(
        enrollmentId: String,
        secret: String,
        termoId: String,
        nome: String,
        cpf: String,
        assinatura: String,
        texto: String,
        reaproveitada: Boolean
    ): JSONObject {
        return post(
            JSONObject()
                .put("action", "assinarTermo")
                .put("enrollment_id", enrollmentId)
                .put("device_secret", secret)
                .put("termo_id", termoId)
                .put("nome", nome)
                .put("cpf", cpf)
                .put("assinatura", assinatura)
                .put("texto", texto)
                .put("reaproveitada", reaproveitada)
        )
    }

    /** Baixa uma imagem (ex.: assinatura armazenada) e devolve os bytes. */
    fun baixarImagem(url: String): ByteArray? {
        return try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        } catch (_: Exception) { null }
    }
}
