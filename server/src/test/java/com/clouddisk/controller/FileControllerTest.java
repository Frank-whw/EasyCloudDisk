package com.clouddisk.file.controller;

import com.clouddisk.common.dto.ApiResponse;
import com.clouddisk.common.dto.FileResponse;
import com.clouddisk.common.dto.FileUploadResponse;
import com.clouddisk.file.service.FileService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FileController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @Test
    void listFiles_shouldReturnSuccess() throws Exception {
        UUID uid = UUID.randomUUID();
        Mockito.when(fileService.getUserFiles(uid)).thenReturn(List.of(new FileResponse()));

        mockMvc.perform(get("/files").principal(() -> uid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void upload_shouldReturnSuccess() throws Exception {
        UUID uid = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes());
        Mockito.when(fileService.uploadFile(Mockito.eq(uid), Mockito.any(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new FileUploadResponse(UUID.randomUUID(), "a.txt", 2L, "/"));

        mockMvc.perform(multipart("/files/upload").file(file).principal(() -> uid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void download_shouldReturnStream() throws Exception {
        UUID uid = UUID.randomUUID();
        UUID fid = UUID.randomUUID();
        Mockito.when(fileService.getFileInfo(uid, fid)).thenReturn(new FileResponse(fid, uid, "a.txt", "/", "s3", 1L, "h", null, null));
        Mockito.when(fileService.openFileStream(uid, fid)).thenReturn(new java.io.ByteArrayInputStream("x".getBytes()));

        mockMvc.perform(get("/files/" + fid + "/download").principal(() -> uid.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("a.txt")));
    }
}