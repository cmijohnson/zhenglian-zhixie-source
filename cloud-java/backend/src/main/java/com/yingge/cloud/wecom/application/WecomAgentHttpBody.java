package com.yingge.cloud.wecom.application;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** A header timeout alone does not bound InputStream reads; bound both bytes and elapsed time. */
final class WecomAgentHttpBody {
    private WecomAgentHttpBody(){}
    static String read(InputStream stream,Duration timeout,int maximumBytes)throws Exception {
        CompletableFuture<String> result=new CompletableFuture<>();
        Thread reader=Thread.ofVirtual().start(()-> {
            try(stream;var buffer=new ByteArrayOutputStream()) {
                byte[] bytes=new byte[8192];int length;
                while((length=stream.read(bytes))!=-1) {
                    if(buffer.size()+length>maximumBytes)throw new IllegalArgumentException("Response exceeds limit");
                    buffer.write(bytes,0,length);
                }
                result.complete(buffer.toString(StandardCharsets.UTF_8));
            }catch(Exception e){result.completeExceptionally(e);}
        });
        try{return result.get(Math.max(1,timeout.toMillis()),TimeUnit.MILLISECONDS);}
        finally{try{stream.close();}finally{reader.interrupt();}}
    }
}
