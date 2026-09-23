package com.calsnap;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import java.util.UUID;

@Controller("/photos")
@ExecuteOn(TaskExecutors.BLOCKING)
public class PhotoApi {
  final Photos photos;

  public PhotoApi(Photos photos) {
    this.photos = photos;
  }

  @Get("/{owner}/{image}.jpg{?expires,signature}")
  public HttpResponse<byte[]> photo(UUID owner, UUID image, long expires, String signature)
      throws Exception {
    return HttpResponse.ok(photos.read(owner, image, expires, signature))
        .contentType(MediaType.IMAGE_JPEG_TYPE)
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff");
  }
}
