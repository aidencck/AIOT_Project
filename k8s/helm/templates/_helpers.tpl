{{/* 公共标签：所有资源 metadata.labels 追加使用（不覆盖 app selector） */}}
{{- define "aiot.standardLabels" -}}
app.kubernetes.io/name: aiot
app.kubernetes.io/part-of: aiot-platform
app.kubernetes.io/managed-by: {{ .Release.Service | quote }}
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end }}

{{/* Pod 级安全上下文 */}}
{{- define "aiot.podSecurityContext" -}}
securityContext:
  runAsNonRoot: {{ .Values.security.runAsNonRoot }}
  runAsUser: {{ .Values.security.runAsUser }}
  runAsGroup: {{ .Values.security.runAsGroup }}
  fsGroup: {{ .Values.security.fsGroup }}
  seccompProfile:
    type: {{ .Values.security.seccompProfile }}
{{- end }}

{{/* 容器级安全上下文 */}}
{{- define "aiot.containerSecurityContext" -}}
securityContext:
  allowPrivilegeEscalation: {{ .Values.security.allowPrivilegeEscalation }}
  capabilities:
    drop:
{{- range .Values.security.capabilitiesDrop }}
      - {{ . }}
{{- end }}
{{- end }}
