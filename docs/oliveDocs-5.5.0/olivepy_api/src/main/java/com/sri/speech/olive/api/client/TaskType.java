package com.sri.speech.olive.api.client;

/**
 */ // Recognition task types - a blend of trait types and 'task' types
public enum TaskType {
    SAD,
    SID,
    LID,
    KWS,
    ENROLL,
    UNENROLL,
    PRELOAD,
    REMOVE,
    AUDIO,
    VECTOR,
    IMPORT,
    EXPORT,
    QBE,
    REGION_SCORE,
    FRAME_SCORE,
    GLOBAL_SCORE,
    APPLY_UPDATE,
    GET_UPDATE_STATUS,
    COMPARE_AUDIO,
    WORKFLOW_ANALYSIS,
    WORKFLOW_ENROLL,
    WORKFLOW_ADAPT,
    TXT_TRANSFORM,
    AUDIO_ALIGN,
    BOUNDING_BOX
}

