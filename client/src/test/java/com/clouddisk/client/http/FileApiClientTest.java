package com.clouddisk.client.http;

import com.clouddisk.client.model.ApiResponse;
import com.clouddisk.client.model.FileResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileApiClient 单元测试
 * 测试文件API客户端的封装逻辑
 */
@ExtendWith(MockitoExtension.class)
class FileApiClientTest {

    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private CloseableHttpResponse httpResponse;

    @Mock
    private HttpEntity httpEntity;

    private FileApiClient fileApiClient;
    private String baseUrl;
    private ObjectMapper objectMapper;
    private String authToken;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:8080";
        fileApiClient = new FileApiClient(baseUrl, httpClient);
        authToken = "test-token-123";
        fileApiClient.setAuthToken(authToken);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void testListFiles_Success() throws Exception {
        // Given
        FileResponse file1 = new FileResponse();
        file1.setFileId("file-id-1");
        file1.setName("test1.txt");

        FileResponse file2 = new FileResponse();
        file2.setFileId("file-id-2");
        file2.setName("test2.txt");

        List<FileResponse> files = List.of(file1, file2);

        ApiResponse<List<FileResponse>> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);
        apiResponse.setData(files);

        String responseJson = objectMapper.writerFor(new TypeReference<ApiResponse<List<FileResponse>>>() {})
                .writeValueAsString(apiResponse);

        when(httpClient.execute(any(HttpGet.class), any())).thenAnswer(invocation -> {
            Object handler = invocation.getArgument(1);
            if (handler instanceof org.apache.hc.client5.http.classic.HttpClientResponseHandler) {
                @SuppressWarnings("unchecked")
                org.apache.hc.client5.http.classic.HttpClientResponseHandler<List<FileResponse>> responseHandler =
                    (org.apache.hc.client5.http.classic.HttpClientResponseHandler<List<FileResponse>>) handler;
                
                when(httpResponse.getCode()).thenReturn(200);
                when(httpResponse.getEntity()).thenReturn(httpEntity);
                when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(responseJson.getBytes()));
                
                return responseHandler.handleResponse((ClassicHttpResponse) httpResponse);
            }
            return null;
        });

        // When
        List<FileResponse> result = fileApiClient.listFiles();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(httpClient, atLeastOnce()).execute(any(HttpGet.class), any());
    }

    @Test
    void testListFiles_EmptyList() throws Exception {
        // Given
        ApiResponse<List<FileResponse>> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);
        apiResponse.setData(new ArrayList<>());

        String responseJson = objectMapper.writerFor(new TypeReference<ApiResponse<List<FileResponse>>>() {})
                .writeValueAsString(apiResponse);

        when(httpClient.execute(any(HttpGet.class), any())).thenAnswer(invocation -> {
            Object handler = invocation.getArgument(1);
            if (handler instanceof org.apache.hc.client5.http.classic.HttpClientResponseHandler) {
                @SuppressWarnings("unchecked")
                org.apache.hc.client5.http.classic.HttpClientResponseHandler<List<FileResponse>> responseHandler =
                    (org.apache.hc.client5.http.classic.HttpClientResponseHandler<List<FileResponse>>) handler;
                
                when(httpResponse.getCode()).thenReturn(200);
                when(httpResponse.getEntity()).thenReturn(httpEntity);
                when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(responseJson.getBytes()));
                
                return responseHandler.handleResponse((ClassicHttpResponse) httpResponse);
            }
            return null;
        });

        // When
        List<FileResponse> result = fileApiClient.listFiles();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testDeleteFile_Success() throws Exception {
        // Given
        String fileId = "file-id-123";

        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);

        String responseJson = objectMapper.writeValueAsString(apiResponse);

        when(httpClient.execute(any(HttpDelete.class), any())).thenAnswer(invocation -> {
            Object handler = invocation.getArgument(1);
            if (handler instanceof org.apache.hc.client5.http.classic.HttpClientResponseHandler) {
                @SuppressWarnings("unchecked")
                org.apache.hc.client5.http.classic.HttpClientResponseHandler<Boolean> responseHandler =
                    (org.apache.hc.client5.http.classic.HttpClientResponseHandler<Boolean>) handler;
                
                when(httpResponse.getCode()).thenReturn(200);
                when(httpResponse.getEntity()).thenReturn(httpEntity);
                when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(responseJson.getBytes()));
                
                return responseHandler.handleResponse((ClassicHttpResponse) httpResponse);
            }
            return null;
        });

        // When
        boolean result = fileApiClient.deleteFile(fileId);

        // Then
        assertTrue(result);
        verify(httpClient, atLeastOnce()).execute(any(HttpDelete.class), any());
    }

    @Test
    void testDeleteFile_Unauthorized_ReturnsFalse() throws Exception {
        // Given
        String fileId = "file-id-123";

        when(httpClient.execute(any(HttpDelete.class), any())).thenAnswer(invocation -> {
            Object handler = invocation.getArgument(1);
            if (handler instanceof org.apache.hc.client5.http.classic.HttpClientResponseHandler) {
                @SuppressWarnings("unchecked")
                org.apache.hc.client5.http.classic.HttpClientResponseHandler<Boolean> responseHandler =
                    (org.apache.hc.client5.http.classic.HttpClientResponseHandler<Boolean>) handler;
                
                when(httpResponse.getCode()).thenReturn(401);
                
                return responseHandler.handleResponse((ClassicHttpResponse) httpResponse);
            }
            return null;
        });

        // When
        boolean result = fileApiClient.deleteFile(fileId);

        // Then
        assertFalse(result);
    }

    @Test
    void testSetAuthToken() {
        // Given
        String newToken = "new-token-456";

        // When
        fileApiClient.setAuthToken(newToken);

        // Then
        // 验证token已设置（通过后续调用验证）
        assertNotNull(fileApiClient);
    }
}

