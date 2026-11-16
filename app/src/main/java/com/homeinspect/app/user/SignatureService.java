package com.homeinspect.app.user;

import com.homeinspect.app.photo.FileStorage;
import com.homeinspect.app.photo.ImageProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** Signature image of each inspector (PNG, transparency kept), printed at the end of the PDF. */
@Service
@RequiredArgsConstructor
public class SignatureService {

    static final String DIR = "signatures";
    private static final int MAX_SIZE = 800;

    private final UserProfileRepository profiles;
    private final ImageProcessor processor;
    private final FileStorage storage;

    @Transactional
    public void save(long profileId, byte[] image) {
        byte[] png = processor.processPng(image, MAX_SIZE);
        UserProfile profile = profiles.findById(profileId).orElseThrow();
        String fileName = profileId + "-" + UUID.randomUUID() + ".png";
        storage.write(storage.resolve(DIR, fileName), png);
        String previous = profile.getSignaturePath();
        profile.setSignaturePath(fileName);
        afterCommit(previous);
    }

    @Transactional
    public void delete(long profileId) {
        UserProfile profile = profiles.findById(profileId).orElseThrow();
        String previous = profile.getSignaturePath();
        profile.setSignaturePath(null);
        afterCommit(previous);
    }

    /** File of the signature of a profile, if any. */
    @Transactional(readOnly = true)
    public Optional<Path> file(long profileId) {
        return profiles.findById(profileId)
                .map(UserProfile::getSignaturePath)
                .map(name -> storage.resolve(DIR, name))
                .filter(Files::isRegularFile);
    }

    private void afterCommit(String previousFile) {
        if (previousFile == null) {
            return;
        }
        Path previous = storage.resolve(DIR, previousFile);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(previous);
            }
        });
    }
}
