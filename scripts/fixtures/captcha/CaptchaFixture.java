import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * 仅供 WP009 Playwright 测试进程调用的受控验证码图片生成器。
 * 不打包进产品、不开放端点、不读 Redis；输入走标准输入，图片走捕获的标准输出。
 * 真实图片生成链另测 GET /captcha，本夹具只确定校验输入，仍使用真实 TTL 和校验器。
 */
public final class CaptchaFixture {
    private CaptchaFixture() {
    }

    public static void main(String[] args) throws Exception {
        String code = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (code == null || !code.matches("[a-z0-9]{4}")) {
            throw new IllegalArgumentException("受控输入必须为四位小写字母或数字");
        }
        BufferedImage image = new BufferedImage(130, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 130, 40);
            graphics.setColor(new Color(30, 64, 120));
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
            graphics.drawString(code, 18, 30);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        // 调用方仅在内存捕获图片，不将标准输出转为日志。
        System.out.write(Base64.getEncoder().encode(png.toByteArray()));
    }
}
