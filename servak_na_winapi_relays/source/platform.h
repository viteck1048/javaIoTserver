#pragma once

#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #include <windows.h>
    #pragma comment(lib, "ws2_32.lib")
    #pragma comment(lib, "sqlite3.lib")
    #define CLOSE_SOCKET closesocket
    #define INVALID_SOCKET_HANDLE INVALID_SOCKET
    #define SOCKET_ERROR_HANDLE SOCKET_ERROR
    #define GET_LAST_ERROR WSAGetLastError()
    #define GET_CURRENT_DIR(buffer, size) GetCurrentDirectoryA(size, buffer)
    #define DIR_SEPARATOR '\\'
    #define SHUT_WR SD_SEND
    #define inet_pton(family, src, dst) InetPtonA(family, src, dst)
    typedef SOCKET socket_t;
#else
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <unistd.h>
    #include <cerrno>
    #include <cstring>
    #include <climits>
    #include <sys/stat.h>
    #include <fcntl.h>
    #define CLOSE_SOCKET close
    #define INVALID_SOCKET_HANDLE (-1)
    #define SOCKET_ERROR_HANDLE (-1)
    #define GET_LAST_ERROR errno
    #define GET_CURRENT_DIR(buffer, size) getcwd(buffer, size)
    #define DIR_SEPARATOR '/'
    #define SHUT_WR SHUT_WR
    #define SD_SEND SHUT_WR
    typedef int socket_t;

    // Define Windows types for Linux
    typedef int BOOL;
    #ifndef TRUE
    #define TRUE 1
    #endif
    #ifndef FALSE
    #define FALSE 0
    #endif
    typedef unsigned long DWORD;
    #define MAX_PATH PATH_MAX
    
    // Windows-specific functions
    #define _mkdir(dir) mkdir(dir, 0777)
    #define _chdir chdir
    #define _getcwd getcwd
    #define _access access
    #define _strdup strdup
    #define _stricmp strcasecmp
    #define _strnicmp strncasecmp
    #define _snprintf snprintf
    #define _vsnprintf vsnprintf
    #define _strlwr strlwr
    #define _strupr strupr
    #define _stricmp strcasecmp
    #define _strnicmp strncasecmp
    #define _strrev(s) { \
        char* start = s; \
        char* end = s + strlen(s) - 1; \
        while (start < end) { \
            char tmp = *start; \
            *start++ = *end; \
            *end-- = tmp; \
        } \
    }
    
    // Windows Sockets stubs for Linux
    #define WSACleanup() (0)
    #define WSAStartup(wVersionRequired, lpWSAData) (0)
    #define MAKEWORD(a, b) ((WORD)(((BYTE)((a) & 0xff)) | ((WORD)((BYTE)((b) & 0xff))) << 8))
    #define WSADATA int
    #define WSAEINPROGRESS EINPROGRESS
    #define WSAEWOULDBLOCK EWOULDBLOCK
    #define WSAECONNRESET ECONNRESET
    #define WSAECONNABORTED ECONNABORTED
    #define WSAEINTR EINTR
    #define WSAEINVAL EINVAL
    #define WSAEMFILE EMFILE
    #define WSAEFAULT EFAULT
    #define WSAENETDOWN ENETDOWN
    #define WSAENOTCONN ENOTCONN
    #define WSAETIMEDOUT ETIMEDOUT
    #define WSAECONNREFUSED ECONNREFUSED
    #define WSAEISCONN EISCONN
    #define WSAEADDRINUSE EADDRINUSE
    #define WSAEADDRNOTAVAIL EADDRNOTAVAIL
    #define WSAENETUNREACH ENETUNREACH
    #define WSAENOBUFS ENOBUFS
    #define WSAENOTCONN ENOTCONN
    #define WSAESHUTDOWN ESHUTDOWN
    #define WSAEOPNOTSUPP EOPNOTSUPP
    #define WSAEMSGSIZE EMSGSIZE
    #define WSAEINPROGRESS EINPROGRESS
    #define WSAEALREADY EALREADY
    #define WSAEISCONN EISCONN
    #define WSAENOTSOCK ENOTSOCK
    #define WSAEDESTADDRREQ EDESTADDRREQ
    #define WSAEMSGSIZE EMSGSIZE
    #define WSAEPROTOTYPE EPROTOTYPE
    #define WSAENOPROTOOPT ENOPROTOOPT
    #define WSAEPROTONOSUPPORT EPROTONOSUPPORT
    #define WSAEOPNOTSUPP EOPNOTSUPP
    #define WSAEAFNOSUPPORT EAFNOSUPPORT
    #define WSAEADDRINUSE EADDRINUSE
    #define WSAEADDRNOTAVAIL EADDRNOTAVAIL
    #define WSAENETDOWN ENETDOWN
    #define WSAENETUNREACH ENETUNREACH
    #define WSAENETRESET ENETRESET
    #define WSAECONNABORTED ECONNABORTED
    #define WSAECONNRESET ECONNRESET
    #define WSAENOBUFS ENOBUFS
    #define WSAEISCONN EISCONN
    #define WSAENOTCONN ENOTCONN
    #define WSAESHUTDOWN ESHUTDOWN
    #define WSAETOOMANYREFS ETOOMANYREFS
    #define WSAETIMEDOUT ETIMEDOUT
    #define WSAECONNREFUSED ECONNREFUSED
    #define WSAELOOP ELOOP
    #define WSAENAMETOOLONG ENAMETOOLONG
    #define WSAEHOSTDOWN EHOSTDOWN
    #define WSAEHOSTUNREACH EHOSTUNREACH
    #define WSAENOTEMPTY ENOTEMPTY
    #define WSAEPROCLIM EPROCLIM
    #define WSAEUSERS EUSERS
    #define WSAEDQUOT EDQUOT
    #define WSAESTALE ESTALE
    #define WSAEREMOTE EREMOTE
    #define WSAEDISCON EDISCON
    #define WSAENOMORE ENOMORE
    #define WSAECANCELLED ECANCELED
    #define WSAEINVALIDPROCTABLE EINVAL
    #define WSAEINVALIDPROVIDER EINVAL
    #define WSAEPROVIDERFAILEDINIT EFAULT
    #define WSASYSCALLFAILURE EFAULT
    #define WSASERVICE_NOT_FOUND ENOENT
    #define WSATYPE_NOT_FOUND ENOENT
    #define WSA_E_NO_MORE ENOENT
    #define WSA_E_CANCELLED ECANCELED
    #define WSAEREFUSED ECONNREFUSED
    #define WSAHOST_NOT_FOUND ENOENT
    #define WSATRY_AGAIN EAGAIN
    #define WSANO_RECOVERY ENOTRECOVERABLE
    #define WSANO_DATA ENODATA
#endif
