# Security Policy

## Supported Versions

We release patches for security vulnerabilities. Which versions are eligible for receiving such patches depends on the CVSS v3.0 Rating:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability, please send an email to [your-email@example.com] with the following information:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

We will respond within 48 hours and work with you to address the issue.

## Security Best Practices

This app follows Android security best practices:

- ✅ Code obfuscation (ProGuard/R8)
- ✅ Resource shrinking
- ✅ No hardcoded secrets
- ✅ Secure data storage (Room database with encryption support)
- ✅ Local-only data (no network transmission)
- ✅ Regular dependency updates

## Data Privacy

- All data is stored locally on the device
- No data is transmitted to external servers
- No analytics or tracking without user consent
- User data is never shared with third parties

