import api from './api';

export interface Message {
  msg_date: number;           // Unix timestamp (message ID)
  msg_type: 'CHAT' | 'COMMENT' | 'LIKE' | 'EVENT' | 'FB';
  msg_user: string;           // Username
  msg_body: string;           // Base64 encoded message
}

export interface ChatResponse {
  messages: Message[];
}

/**
 * Pull messages (chat/comments)
 * @param md5 File hash (empty for global chat)
 * @param msgFrom Last message ID (0 for all)
 * @param multiclusterid Optional cluster ID
 */
export const pullMessages = async (
  md5: string = '',
  msgFrom: number = 0,
  multiclusterid?: string
): Promise<Message[]> => {
  const params: any = {
    md5,
    msg_from: msgFrom,
  };

  if (multiclusterid) {
    params.multiclusterid = multiclusterid;
  }

  const response = await api.get('/cass/chat_pull.fn', { params });

  // Backend returns object with 'messages' array or just array
  const messages = Array.isArray(response.data)
    ? response.data
    : response.data?.messages || [];

  return messages.map((msg: any) => ({
    msg_date: parseInt(msg.msg_date || msg.msgDate || msg.msgdate || '0'),
    msg_type: msg.msg_type || msg.msgType || msg.msgtype || 'CHAT',
    msg_user: msg.msg_user || msg.msgUser || msg.msguser || 'Unknown',
    msg_body: msg.msg_body || msg.msgBody || msg.msgbody || '',
  }));
};

/**
 * Send message
 * @param md5 File hash (empty for global chat)
 * @param msgType Message type
 * @param msgBody Message body (will be base64 encoded)
 * @param multiclusterid Optional cluster ID
 */
export const pushMessage = async (
  md5: string = '',
  msgType: 'CHAT' | 'COMMENT' | 'LIKE' | 'EVENT' = 'CHAT',
  msgBody: string,
  multiclusterid?: string
): Promise<void> => {
  // Base64 encode the message body
  const encodedBody = btoa(unescape(encodeURIComponent(msgBody)));

  const params: any = {
    md5,
    msg_type: msgType,
    msg_body: encodedBody,
    msg_from: Date.now(), // Current timestamp as message ID
  };

  if (multiclusterid) {
    params.multiclusterid = multiclusterid;
  }

  await api.get('/cass/chat_push.fn', { params });
};

/**
 * Clear all chat messages (admin only)
 */
export const clearAllChat = async (): Promise<void> => {
  await api.get('/cass/chat_clear.fn');
};

/**
 * Decode base64 message body and extract clean message
 */
export const decodeMessageBody = (encodedBody: string): string => {
  let decoded: string;

  try {
    // Try to decode as base64
    decoded = decodeURIComponent(escape(atob(encodedBody)));
  } catch (e) {
    // If base64 decoding fails, use the original string
    decoded = encodedBody;
  }

  // Check if it's a JSON-like structure with {'#'msg'#':'#'value'#'}
  // Example: {'#'play'#':false,'#'msg'#':'#'Logged In'#'}

  // Try multiple patterns to extract the message
  const patterns = [
    /'#'msg'#'\s*:\s*'#'([^']+)'#'/,  // {'#'msg'#':'#'value'#'}
    /"#"msg"#"\s*:\s*"#"([^"]+)"#"/,  // {"#"msg"#":"#"value"#"}
    /msg['"]\s*:\s*['"]\s*#\s*['"]\s*([^'"#]+)/,  // Flexible pattern
  ];

  for (const pattern of patterns) {
    const match = decoded.match(pattern);
    if (match) {
      return match[1];
    }
  }

  return decoded;
};
