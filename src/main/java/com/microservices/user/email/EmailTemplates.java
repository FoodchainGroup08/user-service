package com.microservices.user.email;

public class EmailTemplates {

    private static final String BASE_STYLE = """
        <style>
          body{margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;}
          .wrapper{max-width:600px;margin:0 auto;padding:24px 16px;}
          .card{background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 16px rgba(0,0,0,0.08);}
          .header{background:linear-gradient(135deg,#e85d04 0%,#f48c06 100%);padding:40px 32px;text-align:center;}
          .logo{font-size:32px;margin:0 0 8px;}
          .header-title{color:#ffffff;margin:0;font-size:26px;font-weight:700;letter-spacing:-0.5px;}
          .header-sub{color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;}
          .body{padding:40px 32px;}
          .greeting{font-size:20px;font-weight:600;color:#111827;margin:0 0 12px;}
          .text{font-size:15px;color:#4b5563;line-height:1.7;margin:0 0 16px;}
          .btn-wrap{text-align:center;margin:32px 0;}
          .btn{display:inline-block;background:#e85d04;color:#ffffff !important;padding:14px 36px;border-radius:10px;text-decoration:none;font-size:15px;font-weight:600;letter-spacing:0.2px;}
          .btn:hover{background:#c94e03;}
          .divider{border:none;border-top:1px solid #f0f0f0;margin:28px 0;}
          .note{font-size:13px;color:#9ca3af;line-height:1.6;margin:0;}
          .footer{background:#f9fafb;padding:24px 32px;text-align:center;border-top:1px solid #f0f0f0;}
          .footer-text{font-size:12px;color:#9ca3af;margin:0;line-height:1.8;}
          .footer-link{color:#e85d04;text-decoration:none;}
        </style>
        """;

    private static String wrap(String headerExtra, String bodyContent) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1.0">
              <title>FoodChain</title>
              %s%s
            </head>
            <body>
              <div class="wrapper">
                <div class="card">
                  %s
                  <div class="footer">
                    <p class="footer-text">
                      &copy; 2026 FoodChain &nbsp;&bull;&nbsp;
                      You received this email because of activity on your account.<br>
                      If you didn't request this, please ignore this email or <a class="footer-link" href="#">contact support</a>.
                    </p>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(BASE_STYLE, headerExtra, bodyContent);
    }

    public static String welcome(String name, String frontendBaseUrl) {
        String base = frontendBaseUrl != null && !frontendBaseUrl.isBlank()
                ? frontendBaseUrl.replaceAll("/$", "")
                : "http://localhost:5173";
        String body = """
            <div class="header">
              <div class="logo">🍔</div>
              <h1 class="header-title">Welcome to FoodChain!</h1>
              <p class="header-sub">Your food journey starts here</p>
            </div>
            <div class="body">
              <p class="greeting">Hey %s 👋</p>
              <p class="text">
                We're thrilled to have you on board. FoodChain connects you with the best
                restaurant branches near you — order in, dine in, or take away, all in one place.
              </p>
              <p class="text">Here's what you can do right away:</p>
              <ul style="color:#4b5563;font-size:15px;line-height:2;padding-left:20px;">
                <li>📍 Find a branch near you</li>
                <li>🍽️ Browse the full menu</li>
                <li>🛒 Place an order in seconds</li>
                <li>📦 Track your order in real time</li>
              </ul>
              <div class="btn-wrap">
                <a class="btn" href="%s">Start Ordering</a>
              </div>
              <hr class="divider">
              <p class="note">
                If you have any questions, simply reply to this email — we're always happy to help.
              </p>
            </div>
            """.formatted(name != null ? name : "there", base);
        return wrap("", body);
    }

    public static String passwordReset(String name, String resetLink) {
        String body = """
            <div class="header">
              <div class="logo">🔐</div>
              <h1 class="header-title">Reset Your Password</h1>
              <p class="header-sub">A reset was requested for your FoodChain account</p>
            </div>
            <div class="body">
              <p class="greeting">Hi %s,</p>
              <p class="text">
                We received a request to reset the password for your FoodChain account.
                Click the button below to choose a new password. This link expires in <strong>1 hour</strong>.
              </p>
              <div class="btn-wrap">
                <a class="btn" href="%s">Reset My Password</a>
              </div>
              <hr class="divider">
              <p class="note">
                If the button above doesn't work, copy and paste this link into your browser:<br>
                <a href="%s" style="color:#e85d04;word-break:break-all;">%s</a>
              </p>
              <hr class="divider">
              <p class="note" style="color:#ef4444;">
                ⚠️ If you didn't request a password reset, you can safely ignore this email.
                Your password will not change.
              </p>
            </div>
            """.formatted(
                name != null ? name : "there",
                resetLink, resetLink, resetLink
            );
        return wrap("", body);
    }
}
