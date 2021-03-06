/*
 * ****************************************************************************
 *  Copyright © 2015 Hoffmann-La Roche
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ****************************************************************************
 */

package com.roche.iceboar.downloader;

import com.roche.iceboar.IceBoarException;
import com.roche.iceboar.cachestorage.CacheStatus;
import com.roche.iceboar.progressevent.ProgressEvent;
import com.roche.iceboar.progressevent.ProgressEventFactory;
import com.roche.iceboar.progressevent.ProgressEventQueue;
import com.roche.iceboar.runner.ExecutableCommand;
import com.roche.iceboar.runner.ExecutableCommandFactory;
import com.roche.iceboar.settings.GlobalSettings;
import net.lingala.zip4j.exception.ZipException;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.fail;

public class JREDownloaderTest {

    private static final ProgressEvent JRE_DOWNLOAD_EVENT = new ProgressEvent("jre download", "");
    private static final ProgressEvent JRE_DOWNLOADED_EVENT = new ProgressEvent("jre downloaded", "");
    private static final ProgressEvent JRE_UNZIP_EVENT = new ProgressEvent("jre unzip", "");
    private static final ProgressEvent JRE_UNZIPPED_EVENT = new ProgressEvent("jre unzipped", "");

    @Test
    public void shouldTryDownloadJRE() throws IOException {
        // given
        String javaTempDir = System.getProperty("java.io.tmpdir");
        FileUtilsFacade fileUtils = mock(FileUtilsFacade.class);
        GlobalSettings settings = GlobalSettings.builder()
                                                .targetJavaURL("http://www.example.com/jre1.zip")
                                                .tempDirectory(javaTempDir)
                                                .cacheStatus(mock(CacheStatus.class))
                                                .build();
        ProgressEventFactory progressEventFactory = mock(ProgressEventFactory.class);
        when(progressEventFactory.getJREDownloadEvent())
                .thenReturn(JRE_DOWNLOAD_EVENT);
        when(progressEventFactory.getJREDownloadedEvent())
                .thenReturn(JRE_DOWNLOADED_EVENT);
        JREDownloader downloader = new JREDownloader(settings, fileUtils,
                progressEventFactory, mock(ProgressEventQueue.class), mock(ExecutableCommandFactory.class));

        // when
        downloader.update(JRE_DOWNLOAD_EVENT);

        // then
        verify(fileUtils)
                .saveContentFromURLToFile(new URL("http://www.example.com/jre1.zip"), new File(tempDirPlusFilename("jre1.zip")));
    }

    @Test
    public void shouldTryExtractDownloadedJRE() throws ZipException, InterruptedException {
        // given
        String javaTempDir = System.getProperty("java.io.tmpdir");
        FileUtilsFacade fileUtils = mock(FileUtilsFacade.class);
        GlobalSettings settings = GlobalSettings.builder()
                                                .targetJavaURL("http://www.example.com/jre1.zip")
                                                .tempDirectory(javaTempDir)
                                                .cacheStatus(mock(CacheStatus.class))
                                                .build();
        ProgressEventFactory progressEventFactory = mock(ProgressEventFactory.class);
        when(progressEventFactory.getJREUnzipEvent())
                .thenReturn(JRE_UNZIP_EVENT);
        when(progressEventFactory.getJREUnzippedEvent())
                .thenReturn(JRE_UNZIPPED_EVENT);
        ExecutableCommandFactory executableCommandFactory = mock(ExecutableCommandFactory.class);
        ExecutableCommand javaCheckVersionCommand = mock(ExecutableCommand.class);
        Process processMock = mock(Process.class);
        when(processMock.waitFor())
                .thenReturn(1);             // unzipping is necessary
        when(javaCheckVersionCommand.exec())
                .thenReturn(processMock);
        when(executableCommandFactory.createJavaGetVersionNumberCommand(anyString()))
                .thenReturn(javaCheckVersionCommand);

        JREDownloader downloader = new JREDownloader(settings, fileUtils,
                progressEventFactory, mock(ProgressEventQueue.class), executableCommandFactory);

        // when
        downloader.update(JRE_UNZIP_EVENT);

        // then
        verify(fileUtils)
                .extractZipFile(tempDirPlusFilename("jre1.zip"), tempDirPlusFilename("jre1_0"));
    }

    @Test
    public void shouldDoesntDownloadWhenTargetJavaUrlIsBlank() throws IOException {
        // given
        FileUtilsFacade fileUtils = mock(FileUtilsFacade.class);
        GlobalSettings settings = GlobalSettings.builder()
                                                .targetJavaURL(" ")
                                                .tempDirectory("/tmp")
                                                .cacheStatus(mock(CacheStatus.class))
                                                .build();
        ProgressEventFactory progressEventFactory = mock(ProgressEventFactory.class);
        when(progressEventFactory.getJREDownloadEvent())
                .thenReturn(JRE_DOWNLOAD_EVENT);
        JREDownloader downloader = new JREDownloader(settings, fileUtils,
                progressEventFactory, mock(ProgressEventQueue.class), mock(ExecutableCommandFactory.class));

        try {
            // when
            downloader.update(JRE_DOWNLOAD_EVENT);
            fail("If should throw a RuntimeException");
        } catch (RuntimeException e) {
            // then
            e.printStackTrace();
            assertThat(e).isInstanceOf(RuntimeException.class)
                         .hasMessage("Please define jnlp.IceBoar.targetJavaURL");
        }
    }

    @Test
    public void shouldThrowExceptionWhenUserTempDirIsNotDefined() {
        // given
        FileUtilsFacade fileUtils = mock(FileUtilsFacade.class);
        GlobalSettings settings = GlobalSettings.builder()
                                                .targetJavaURL("http://www.example.com/jre.zip")
                                                .cacheStatus(mock(CacheStatus.class))
                                                .build();
        ProgressEventFactory progressEventFactory = mock(ProgressEventFactory.class);
        when(progressEventFactory.getJREDownloadEvent())
                .thenReturn(JRE_DOWNLOAD_EVENT);
        JREDownloader downloader = new JREDownloader(settings, fileUtils,
                progressEventFactory, mock(ProgressEventQueue.class), mock(ExecutableCommandFactory.class));

        try {
            // when
            downloader.update(JRE_DOWNLOAD_EVENT);
            fail("Should throw BootstrapException");
        } catch (IceBoarException e) {
            // then
            assertThat(e.getMessage())
                    .isEqualTo("User temp directory is not defined!");
        }
    }

    private String tempDirPlusFilename(String filename) {
        String javaTempDir = System.getProperty("java.io.tmpdir");
        String javaTemp = javaTempDir.endsWith(File.separator) ? javaTempDir : javaTempDir + File.separator;
        return javaTemp + filename;
    }

}