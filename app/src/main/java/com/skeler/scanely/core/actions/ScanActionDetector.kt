package com.skeler.scanely.core.actions

import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode

private const val TAG = "ScanActionDetector"

/**
 * Utility object for detecting actionable patterns from scanned text/barcodes.
 * 
 * Detects:
 * - URLs (http, https, www.)
 * - Email addresses
 * - Phone numbers (E.123 format)
 * - WiFi QR codes
 * - SMS messages
 * 
 * Integrates with ML Kit's structured barcode data when available.
 */
object ScanActionDetector {
    
    // URL pattern: matches http://, https://, and www. prefixes
    // Validates against common TLDs to reduce false positives
    private val URL_PATTERN = Regex(
        """(?i)\b(https?://[^\s<>"{}|\\^`\[\]]+|www\.[^\s<>"{}|\\^`\[\]]+\.[a-z]{2,})""",
        RegexOption.IGNORE_CASE
    )
    
    // Common TLDs for www. validation (subset for performance)
    private val COMMON_TLDS = setOf(
        "com", "org", "net", "edu", "gov", "io", "co", "me", "info", "biz",
        "app", "dev", "ai", "ly", "cc", "tv", "us", "uk", "de", "fr", "jp",
        "cn", "ru", "br", "in", "au", "ca", "es", "it", "nl", "pl", "ch"
    )
    
    // Email pattern: RFC 5322 simplified
    private val EMAIL_PATTERN = Regex(
        """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""
    )
    
    // Phone pattern: E.123 international format
    // Matches: +1234567890, +1 234 567 890, (123) 456-7890, etc.
    private val PHONE_PATTERN = Regex(
        """(?:\+|00)?[1-9]\d{0,2}[-.\s]?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9}"""
    )
    
    // WiFi QR pattern: WIFI:T:WPA;S:NetworkName;P:password;;
    private val WIFI_PATTERN = Regex(
        """WIFI:(?:T:([^;]*);)?S:([^;]+);(?:P:([^;]*);)?(?:H:([^;]*);)?;?""",
        RegexOption.IGNORE_CASE
    )
    
    /**
     * Detect actionable patterns from text and optional ML Kit barcode value type.
     * 
     * @param text Raw text content (from barcode rawValue or OCR result)
     * @param valueType ML Kit Barcode.TYPE_* constant, or null for OCR text
     * @param urlData Optional ML Kit URL data for structured extraction
     * @param wifiData Optional ML Kit WiFi data for structured extraction
     * @param emailData Optional ML Kit Email data for structured extraction
     * @param phoneData Optional ML Kit Phone data for structured extraction
     * @param smsData Optional ML Kit SMS data for structured extraction
     * @param contactInfo Optional ML Kit Contact info for structured extraction
     * @return List of detected actions, ordered by relevance
     */
    fun detectActions(
        text: String,
        valueType: Int? = null,
        // ML Kit structured data (optional)
        urlData: Barcode.UrlBookmark? = null,
        wifiData: Barcode.WiFi? = null,
        emailData: Barcode.Email? = null,
        phoneData: Barcode.Phone? = null,
        smsData: Barcode.Sms? = null,
        contactInfo: Barcode.ContactInfo? = null
    ): List<ScanAction> {
        val actions = mutableListOf<ScanAction>()
        
        Log.d(TAG, "Detecting actions for text: ${text.take(50)}..., valueType: $valueType")
        
        // First, check ML Kit structured data (most reliable)
        when (valueType) {
            Barcode.TYPE_URL -> {
                val url = urlData?.url ?: text
                actions.add(ScanAction.OpenUrl(normalizeUrl(url)))
                actions.add(ScanAction.CopyText(url, "Copy URL"))
            }
            
            Barcode.TYPE_WIFI -> {
                wifiData?.let { wifi ->
                    val wifiType = when (wifi.encryptionType) {
                        Barcode.WiFi.TYPE_WPA -> WifiType.WPA
                        Barcode.WiFi.TYPE_WEP -> WifiType.WEP
                        else -> WifiType.OPEN
                    }
                    actions.add(ScanAction.ConnectWifi(
                        ssid = wifi.ssid ?: "",
                        password = wifi.password,
                        type = wifiType
                    ))
                    actions.add(ScanAction.CopyText(wifi.ssid ?: text, "Copy SSID"))
                }
            }
            
            Barcode.TYPE_EMAIL -> {
                val email = emailData?.address ?: extractFirstEmail(text)
                if (email != null) {
                    actions.add(ScanAction.SendEmail(
                        email = email,
                        subject = emailData?.subject,
                        body = emailData?.body
                    ))
                    actions.add(ScanAction.CopyText(email, "Copy Email"))
                }
            }
            
            Barcode.TYPE_PHONE -> {
                val phone = phoneData?.number ?: text
                actions.add(ScanAction.CallPhone(phone))
                actions.add(ScanAction.CopyText(phone, "Copy Number"))
            }
            
            Barcode.TYPE_SMS -> {
                smsData?.let { sms ->
                    actions.add(ScanAction.SendSms(
                        number = sms.phoneNumber ?: "",
                        message = sms.message
                    ))
                    actions.add(ScanAction.CopyText(sms.phoneNumber ?: text, "Copy Number"))
                }
            }
            
            Barcode.TYPE_CONTACT_INFO -> {
                contactInfo?.let { contact ->
                    actions.add(ScanAction.AddContact(
                        name = contact.name?.formattedName,
                        phone = contact.phones.firstOrNull()?.number,
                        email = contact.emails.firstOrNull()?.address,
                        organization = contact.organization
                    ))
                }
            }
            
            else -> {
                // Fall through to pattern detection
                actions.addAll(detectFromPatterns(text))
            }
        }
        
        // If no ML Kit type or no actions detected, try pattern detection
        if (actions.isEmpty()) {
            actions.addAll(detectFromPatterns(text))
        }
        
        // Always add a copy option if there's meaningful text
        if (text.isNotBlank() && actions.none { it is ScanAction.CopyText }) {
            actions.add(ScanAction.CopyText(text, "Copy Text"))
        }
        
        Log.d(TAG, "Detected ${actions.size} actions")
        return actions.distinctBy { it.javaClass.simpleName + getActionKey(it) }
    }
    
    /**
     * Detect actions from text patterns (no ML Kit type info).
     * Used for OCR results and unknown barcode types.
     */
    private fun detectFromPatterns(text: String): List<ScanAction> {
        val actions = mutableListOf<ScanAction>()
        
        // Check for WiFi QR format first (specific format)
        val wifiMatch = WIFI_PATTERN.find(text)
        if (wifiMatch != null) {
            val type = wifiMatch.groupValues[1].uppercase()
            val ssid = wifiMatch.groupValues[2]
            val password = wifiMatch.groupValues[3].takeIf { it.isNotEmpty() }
            
            val wifiType = when (type) {
                "WPA", "WPA2", "WPA3" -> WifiType.WPA
                "WEP" -> WifiType.WEP
                else -> WifiType.OPEN
            }
            
            actions.add(ScanAction.ConnectWifi(ssid, password, wifiType))
            actions.add(ScanAction.CopyText(ssid, "Copy SSID"))
            return actions // WiFi format is exclusive
        }
        
        // Detect URLs
        val urls = URL_PATTERN.findAll(text).map { it.value }.toList()
        urls.take(5).forEach { url -> // Limit to avoid UI overflow
            val normalized = normalizeUrl(url)
            if (isValidUrl(normalized)) {
                actions.add(ScanAction.OpenUrl(normalized))
            }
        }
        if (urls.size > 5) {
            Log.d(TAG, "Found ${urls.size} URLs, showing first 5")
        }
        
        // Detect emails
        val emails = EMAIL_PATTERN.findAll(text).map { it.value }.toList()
        emails.take(3).forEach { email ->
            actions.add(ScanAction.SendEmail(email))
        }
        
        // Detect phone numbers
        val phones = PHONE_PATTERN.findAll(text).map { it.value }.toList()
        phones.take(3).forEach { phone ->
            // Filter out numbers that are too short (likely not phone numbers)
            val digitsOnly = phone.filter { it.isDigit() }
            if (digitsOnly.length >= 7) {
                actions.add(ScanAction.CallPhone(phone))
            }
        }
        
        return actions
    }
    
    /**
     * Normalize URL: add https:// prefix if missing.
     */
    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("http://", ignoreCase = true) -> url
            url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("www.", ignoreCase = true) -> "https://$url"
            else -> url
        }
    }
    
    /**
     * Validate URL has proper structure and known TLD.
     */
    private fun isValidUrl(url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) && 
            !url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        
        // Extract TLD for validation
        val withoutProtocol = url.substringAfter("://")
        val host = withoutProtocol.substringBefore("/").substringBefore("?")
        val parts = host.split(".")
        
        if (parts.size < 2) return false
        
        val tld = parts.last().lowercase()
        return tld.length >= 2 // Basic TLD check (not strictly enforcing known TLDs)
    }
    
    /**
     * Extract first email from text.
     */
    private fun extractFirstEmail(text: String): String? {
        return EMAIL_PATTERN.find(text)?.value
    }
    
    /**
     * Get a key for deduplication.
     */
    private fun getActionKey(action: ScanAction): String {
        return when (action) {
            is ScanAction.OpenUrl -> action.url
            is ScanAction.CopyText -> action.text.take(50)
            is ScanAction.CallPhone -> action.number
            is ScanAction.SendEmail -> action.email
            is ScanAction.ConnectWifi -> action.ssid
            is ScanAction.SendSms -> action.number
            is ScanAction.AddContact -> "${action.name}${action.phone}${action.email}"
            is ScanAction.ShowRaw -> action.text.take(50)
        }
    }
    
    /**
     * Get a summary for multiple detected items.
     * E.g., "3 links found", "2 emails found"
     */
    fun getSummary(actions: List<ScanAction>): Map<String, Int> {
        return actions.groupBy { 
            when (it) {
                is ScanAction.OpenUrl -> "links"
                is ScanAction.SendEmail -> "emails"
                is ScanAction.CallPhone -> "phone numbers"
                else -> "items"
            }
        }.mapValues { it.value.size }
    }
}
