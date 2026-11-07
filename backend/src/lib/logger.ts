import winston from 'winston';

export type Logger = winston.Logger;

/** Builds the application logger: pretty output in development, JSON elsewhere. */
export function createLogger(level: string, nodeEnv: string): Logger {
  const pretty = winston.format.combine(
    winston.format.colorize(),
    winston.format.timestamp({ format: 'HH:mm:ss' }),
    winston.format.printf(({ timestamp, level: lvl, message, ...meta }) => {
      const rest = Object.keys(meta).length ? ` ${JSON.stringify(meta)}` : '';
      return `${timestamp} ${lvl} ${message}${rest}`;
    }),
  );
  const json = winston.format.combine(winston.format.timestamp(), winston.format.errors({ stack: true }), winston.format.json());

  return winston.createLogger({
    level,
    silent: nodeEnv === 'test' && level === 'silent',
    format: nodeEnv === 'development' ? pretty : json,
    transports: [new winston.transports.Console()],
  });
}
