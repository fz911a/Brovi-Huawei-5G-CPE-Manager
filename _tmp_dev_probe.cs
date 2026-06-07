using System;
using System.Text;
using System.IO;
using System.Net;
using System.Collections.Generic;
using System.Security.Cryptography;

public class DevProbe
{
    public static void Main()
    {
        string baseUrl = "http://10.0.0.1";
        string user = "admin";
        string pass = "12344321";

        var r1 = Send("GET", baseUrl + "/api/webserver/SesTokInfo", null, null);
        string tok = GetXmlValue(r1.Body, "TokInfo");
        string ses = GetXmlValue(r1.Body, "SesInfo");
        if (string.IsNullOrWhiteSpace(tok) || string.IsNullOrWhiteSpace(ses))
            throw new Exception("failed SesTokInfo: " + r1.Body);

        string firstnonce = RandomHex(32);
        string challengeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><username>" + user + "</username><firstnonce>" + firstnonce + "</firstnonce><mode>1</mode><loginflag>2</loginflag></request>";
        var h2 = new Dictionary<string,string>();
        h2["__RequestVerificationToken"] = tok;
        h2["Cookie"] = "SessionID=" + ses;
        var r2 = Send("POST", baseUrl + "/api/user/challenge_login", challengeXml, h2);
        if (r2.Body.Contains("<error>")) throw new Exception("challenge_login failed: " + r2.Body);

        string saltHex = GetXmlValue(r2.Body, "salt");
        string iterationsText = GetXmlValue(r2.Body, "iterations");
        string servernonce = GetXmlValue(r2.Body, "servernonce");
        int iterations = 0;
        int.TryParse(iterationsText, out iterations);
        if (string.IsNullOrWhiteSpace(saltHex) || iterations <= 0 || string.IsNullOrWhiteSpace(servernonce))
            throw new Exception("invalid challenge response: " + r2.Body);

        if (r2.Headers.ContainsKey("Set-Cookie"))
        {
            var setc = r2.Headers["Set-Cookie"];
            int p = setc.IndexOf("SessionID=");
            if (p >= 0)
            {
                string sub = setc.Substring(p + "SessionID=".Length);
                int sidx = sub.IndexOf(';');
                ses = sidx >= 0 ? sub.Substring(0, sidx) : sub;
            }
        }
        string tok2 = r2.Headers.ContainsKey("__RequestVerificationToken") ? r2.Headers["__RequestVerificationToken"] : tok;

        byte[] salt = HexToBytes(saltHex);
        byte[] saltedPassword = PBKDF2_HMAC_SHA256(pass, salt, iterations, 32);
        string authMessage = firstnonce + "," + servernonce + "," + servernonce;

        // Same argument order as app logic: HMAC(key="Client Key", data=saltedPassword)
        byte[] clientKey = HmacSha256(Encoding.UTF8.GetBytes("Client Key"), saltedPassword);
        byte[] storedKey = SHA256Hash(clientKey);
        byte[] clientSignature = HmacSha256(Encoding.UTF8.GetBytes(authMessage), storedKey);
        byte[] clientProof = Xor(clientKey, clientSignature);
        string clientProofHex = BytesToHex(clientProof);

        string authXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><clientproof>" + clientProofHex + "</clientproof><finalnonce>" + servernonce + "</finalnonce><loginflag>2</loginflag></request>";
        var h3 = new Dictionary<string,string>();
        h3["__RequestVerificationToken"] = tok2;
        h3["Cookie"] = "SessionID=" + ses;
        var r3 = Send("POST", baseUrl + "/api/user/authentication_login", authXml, h3);
        if (r3.Body.Contains("<error>")) throw new Exception("authentication_login failed: " + r3.Body);

        if (r3.Headers.ContainsKey("Set-Cookie"))
        {
            var setc = r3.Headers["Set-Cookie"];
            int p = setc.IndexOf("SessionID=");
            if (p >= 0)
            {
                string sub = setc.Substring(p + "SessionID=".Length);
                int sidx = sub.IndexOf(';');
                ses = sidx >= 0 ? sub.Substring(0, sidx) : sub;
            }
        }

        string postTok = null;
        if (r3.Headers.ContainsKey("__RequestVerificationToken")) postTok = r3.Headers["__RequestVerificationToken"];
        if (string.IsNullOrWhiteSpace(postTok) && r3.Headers.ContainsKey("__RequestVerificationTokenone")) postTok = r3.Headers["__RequestVerificationTokenone"];
        if (string.IsNullOrWhiteSpace(postTok)) postTok = tok2;
        if (postTok.Contains("#")) postTok = postTok.Split('#')[0];
        if (postTok.Length > 32) postTok = postTok.Substring(32);

        Console.WriteLine("LOGIN_OK SessionID=" + ses + " tokenLen=" + postTok.Length);

        string[] eps = new string[] {
            "/api/developer/developermode-featureswitch",
            "/api/developer/webapp-support-module",
            "/api/developer/ps-slow",
            "/api/developer/log-status",
            "/api/developer/modem-log",
            "/api/developer/webapp-log",
            "/api/developer/export-log",
            "/api/developer/atport-status",
            "/api/device/datalock",
            "/config/network/networkmode.xml",
            "/api/user/second_login"
        };

        foreach (var ep in eps)
        {
            var hh = new Dictionary<string,string>();
            hh["Cookie"] = "SessionID=" + ses;
            hh["__RequestVerificationToken"] = postTok;
            var rr = Send("GET", baseUrl + ep, null, hh);
            Console.WriteLine("\n===== " + ep + " =====");
            var txt = rr.Body ?? "";
            if (txt.Length > 1500) txt = txt.Substring(0, 1500);
            Console.WriteLine(txt);
        }
    }

    static (string Body, Dictionary<string,string> Headers, int Status) Send(string method, string url, string body, Dictionary<string,string> headers)
    {
        var req = (HttpWebRequest)WebRequest.Create(url);
        req.Method = method;
        req.Timeout = 8000;
        req.ReadWriteTimeout = 8000;
        req.Accept = "*/*";
        req.UserAgent = "Mozilla/5.0";
        if (headers != null)
        {
            foreach (var kv in headers)
            {
                if (kv.Key.Equals("Content-Type", StringComparison.OrdinalIgnoreCase)) req.ContentType = kv.Value;
                else req.Headers[kv.Key] = kv.Value;
            }
        }

        if (!string.IsNullOrEmpty(body))
        {
            byte[] bytes = Encoding.UTF8.GetBytes(body);
            req.ContentType = "application/xml";
            req.ContentLength = bytes.Length;
            using (var s = req.GetRequestStream()) s.Write(bytes, 0, bytes.Length);
        }

        HttpWebResponse resp = null;
        try
        {
            resp = (HttpWebResponse)req.GetResponse();
        }
        catch (WebException ex)
        {
            if (ex.Response != null) resp = (HttpWebResponse)ex.Response;
            else throw;
        }

        var h = new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
        foreach (string key in resp.Headers.AllKeys)
        {
            h[key] = resp.Headers[key];
        }

        string text = "";
        using (var sr = new StreamReader(resp.GetResponseStream())) text = sr.ReadToEnd();
        int status = (int)resp.StatusCode;
        resp.Close();
        return (text, h, status);
    }

    static string GetXmlValue(string xml, string tag)
    {
        string open = "<" + tag + ">";
        string close = "</" + tag + ">";
        int i = xml.IndexOf(open, StringComparison.OrdinalIgnoreCase);
        if (i < 0) return "";
        i += open.Length;
        int j = xml.IndexOf(close, i, StringComparison.OrdinalIgnoreCase);
        if (j < 0) return "";
        return xml.Substring(i, j - i);
    }

    static string RandomHex(int byteLen)
    {
        byte[] b = new byte[byteLen];
        using (var rng = RandomNumberGenerator.Create()) rng.GetBytes(b);
        return BytesToHex(b);
    }

    static byte[] HexToBytes(string hex)
    {
        hex = hex.Trim();
        byte[] bytes = new byte[hex.Length / 2];
        for (int i = 0; i < bytes.Length; i++)
            bytes[i] = Convert.ToByte(hex.Substring(i * 2, 2), 16);
        return bytes;
    }

    static string BytesToHex(byte[] data)
    {
        var sb = new StringBuilder(data.Length * 2);
        foreach (var b in data) sb.Append(b.ToString("x2"));
        return sb.ToString();
    }

    static byte[] HmacSha256(byte[] key, byte[] data)
    {
        using (var h = new HMACSHA256(key)) return h.ComputeHash(data);
    }

    static byte[] SHA256Hash(byte[] data)
    {
        using (var s = SHA256.Create()) return s.ComputeHash(data);
    }

    static byte[] Xor(byte[] a, byte[] b)
    {
        int n = Math.Min(a.Length, b.Length);
        byte[] o = new byte[n];
        for (int i = 0; i < n; i++) o[i] = (byte)(a[i] ^ b[i]);
        return o;
    }

    static byte[] PBKDF2_HMAC_SHA256(string password, byte[] salt, int iterations, int keyLen)
    {
        byte[] pw = Encoding.UTF8.GetBytes(password);
        int hLen = 32;
        int l = (int)Math.Ceiling((double)keyLen / hLen);
        byte[] dk = new byte[keyLen];
        int offset = 0;

        using (var hmac = new HMACSHA256(pw))
        {
            for (int i = 1; i <= l; i++)
            {
                byte[] saltInt = new byte[salt.Length + 4];
                Buffer.BlockCopy(salt, 0, saltInt, 0, salt.Length);
                saltInt[salt.Length + 0] = (byte)(i >> 24);
                saltInt[salt.Length + 1] = (byte)(i >> 16);
                saltInt[salt.Length + 2] = (byte)(i >> 8);
                saltInt[salt.Length + 3] = (byte)(i);

                byte[] u = hmac.ComputeHash(saltInt);
                byte[] t = (byte[])u.Clone();

                for (int j = 1; j < iterations; j++)
                {
                    u = hmac.ComputeHash(u);
                    for (int k = 0; k < hLen; k++) t[k] ^= u[k];
                }

                int toCopy = Math.Min(hLen, keyLen - offset);
                Buffer.BlockCopy(t, 0, dk, offset, toCopy);
                offset += toCopy;
            }
        }
        return dk;
    }
}
