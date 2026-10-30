# Phase 2 AI-only RED

Log: logs/phase2-ai-red.log; BUILD FAILED in 14s. Four selected AI test classes only. No app/search task or provider network beyond localhost fixture.

## app.amber.ai.provider.providers.ProviderBodyCancellationTest

- googleCompletionCancelsWhileReadingResponseBody: java.net.ProtocolException: unexpected end of stream
- claudeCompletionCancelsWhileReadingResponseBody: java.net.ProtocolException: unexpected end of stream
- googleImageGenerationCancelsWhileReadingResponseBody: java.net.ProtocolException: unexpected end of stream
- chatCompletionCancelsWhileReadingResponseBody: java.net.ProtocolException: unexpected end of stream
- openAiImageGenerationCancelsWhileReadingResponseBody: java.net.ProtocolException: unexpected end of stream

## app.amber.ai.provider.providers.ProviderContentReplayTest

- mistralCompletionRetainsAllThinkingAndAnswerChunksInOrder: org.junit.ComparisonFailure: expected:<check [twice]> but was:<check []>
- geminiStreamKeepsEmptySignedTextAfterVisibleAnswer: java.lang.AssertionError: expected:<tail-signature> but was:<null>
- geminiCompletionReplaysSignedTextAndImageTogether: java.lang.AssertionError: expected:<[text-signature, image-signature]> but was:<[null, image-signature]>
- mistralStreamRetainsTextArrayDeltas: org.junit.ComparisonFailure: expected:<[42]> but was:<[]>
- geminiStreamDoesNotMergeDistinctSignedTextParts: java.lang.AssertionError: expected:<[first, second]> but was:<[firstsecond]>

## app.amber.ai.provider.providers.openai.ResponseAPIStoredProtocolRegressionTest

- createdIdentityIsDurableBeforeAnyVisibleOutput: java.lang.AssertionError: expected:<ResponseCursor(responseId=resp_1, sequence=0, providerId=b22b9a26-bfd0-48fb-bef1-b7bd97519e28)> but was:<null>
- disconnectAfterCreatedBeforeTextCanReconnectAndKeepResponseIdentity: java.lang.AssertionError: Created response must be resolvable for the existing single reconnect expected:<2> but was:<1>
- suffixReplayUsesServerCursorAndCheckpointsWithoutACreatedEvent: java.lang.AssertionError: expected:<true> but was:<null>
- onlyOptedInOfficialStreamingCreationUsesBackgroundMode: java.lang.AssertionError: expected:<true> but was:<false>
- fullRebuildRequestsSseWithoutSkippingTheDurablyMissingPrefix: java.lang.AssertionError: expected:<true> but was:<null>

## app.amber.ai.util.KeyRouletteTest

- firstNewWriteRemovesLegacyPlaintextForAllProviders: java.lang.AssertionError: Legacy key for the active provider must be scrubbed
- lruRotatesKeysWithoutPersistingTheirPlaintext: java.lang.AssertionError

Counts: {'tests': 18, 'failures': 17, 'errors': 0, 'skipped': 0}

ContentReplay (5), StoredProtocol (5), KeyRoulette (2) failed on intended assertions. BodyCancellation (5) reached actual provider body.string but fixture cleanup closes the held body and a child ProtocolException masks the cancellation timeout; author is correcting test-only failure reporting before counting these as clean RED evidence.

## Corrected body fixture RED

Log logs/phase2-body-cancellation-red.log; BUILD FAILED in 3s. Five cases all fail the intended call cancellation assertion after the real body read begins:

- googleCompletionCancelsWhileReadingResponseBody: java.lang.AssertionError: Cancellation must reach the active HTTP Call after headers
- claudeCompletionCancelsWhileReadingResponseBody: java.lang.AssertionError: Cancellation must reach the active HTTP Call after headers
- googleImageGenerationCancelsWhileReadingResponseBody: java.lang.AssertionError: Cancellation must reach the active HTTP Call after headers
- chatCompletionCancelsWhileReadingResponseBody: java.lang.AssertionError: Cancellation must reach the active HTTP Call after headers
- openAiImageGenerationCancelsWhileReadingResponseBody: java.lang.AssertionError: Cancellation must reach the active HTTP Call after headers

Fixture uses supervisorScope so cleanup EOF cannot replace the primary assertion. AI1 production untouched at this check.
