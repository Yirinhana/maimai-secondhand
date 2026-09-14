import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

/** Web delivery encoding only. The generated original files are always retained. */
public class PrepareDemoImages {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        Path input=root.resolve(".local/media-043/selected");
        Path catalog=root.resolve("backend/src/main/resources/demo/catalog-v2");
        Path avatars=root.resolve("frontend/src/assets/avatars");
        Files.createDirectories(catalog);Files.createDirectories(avatars);
        int count=0;
        try(var files=Files.list(input)) {
            for(Path file:files.sorted().toList()) {
                String name=file.getFileName().toString();
                boolean avatar=name.matches("avatar-(cat|dog|capybara|owl)\\.png");
                if(!avatar&&!name.matches("item-\\d{3}-(front|back|side)\\.png"))continue;
                Path target=avatar?avatars.resolve(name.substring(7).replace(".png",".jpg"))
                    :catalog.resolve("demo043-"+name.replace(".png",".jpg"));
                if(Files.exists(target))continue;
                BufferedImage original=ImageIO.read(file.toFile());
                if(original==null)throw new IllegalStateException("Unreadable generated image");
                int max=avatar?384:1200;
                double scale=Math.min(1,(double)max/Math.max(original.getWidth(),original.getHeight()));
                BufferedImage encoded=new BufferedImage((int)Math.round(original.getWidth()*scale),(int)Math.round(original.getHeight()*scale),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=encoded.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,encoded.getWidth(),encoded.getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(original,0,0,encoded.getWidth(),encoded.getHeight(),null);g.dispose();
                var writer=ImageIO.getImageWritersByFormatName("jpeg").next();
                var settings=writer.getDefaultWriteParam();settings.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);settings.setCompressionQuality(.86f);
                try(ImageOutputStream out=ImageIO.createImageOutputStream(Files.newOutputStream(target,StandardOpenOption.CREATE_NEW))) {
                    writer.setOutput(out);writer.write(null,new IIOImage(encoded,null,null),settings);
                }finally{writer.dispose();}
                BufferedImage checked=ImageIO.read(target.toFile());
                if(checked==null||checked.getWidth()!=encoded.getWidth()||Files.size(target)>1048576)throw new IllegalStateException("Website encoding check failed: "+target.getFileName());
                System.out.println(target.getFileName()+" "+encoded.getWidth()+"x"+encoded.getHeight()+" "+Files.size(target)+" bytes");count++;
            }
        }
        System.out.println("NEW_WEB_IMAGES="+count);
    }
}
