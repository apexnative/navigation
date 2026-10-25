#import "ANNTransformParts.h"
#import <React/RCTLog.h>
#include <algorithm>
#include <cmath>

// The math works on m[i][j] = m(i+1)(j+1) of `CATransform3D`: a row is what a point's coordinate multiplies (row
// vectors), the layout of the decomposition's own pseudo-code (its matrix[3][0..2] is the translation).
typedef double ANNMatrix[4][4];

static void ANNMatrixOf(CATransform3D t, ANNMatrix m) {
    const CGFloat values[16] = {t.m11, t.m12, t.m13, t.m14, t.m21, t.m22, t.m23, t.m24,
                                t.m31, t.m32, t.m33, t.m34, t.m41, t.m42, t.m43, t.m44};
    for (int i = 0; i < 16; i++) {
        m[i / 4][i % 4] = values[i];
    }
}

static CATransform3D ANNTransformOfMatrix(const ANNMatrix m) {
    CATransform3D t;
    t.m11 = m[0][0];
    t.m12 = m[0][1];
    t.m13 = m[0][2];
    t.m14 = m[0][3];
    t.m21 = m[1][0];
    t.m22 = m[1][1];
    t.m23 = m[1][2];
    t.m24 = m[1][3];
    t.m31 = m[2][0];
    t.m32 = m[2][1];
    t.m33 = m[2][2];
    t.m34 = m[2][3];
    t.m41 = m[3][0];
    t.m42 = m[3][1];
    t.m43 = m[3][2];
    t.m44 = m[3][3];
    return t;
}

static void ANNMatrixIdentity(ANNMatrix m) {
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            m[i][j] = i == j ? 1 : 0;
        }
    }
}

// a · b: the point goes through a, then through b.
static void ANNMatrixMultiply(const ANNMatrix a, const ANNMatrix b, ANNMatrix out) {
    ANNMatrix product;
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            double sum = 0;
            for (int k = 0; k < 4; k++) {
                sum += a[i][k] * b[k][j];
            }
            product[i][j] = sum;
        }
    }
    memcpy(out, product, sizeof(ANNMatrix));
}

// The inverse of m into `out`; NO when m has none.
static BOOL ANNMatrixInvert(const ANNMatrix m, ANNMatrix out) {
    const double *a = &m[0][0];
    double inv[16];
    inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] +
             a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10];
    inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] -
             a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10];
    inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] +
             a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
    inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] -
              a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
    inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] -
             a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
    inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] +
             a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
    inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] -
             a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
    inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] +
              a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
    inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] +
             a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
    inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] -
             a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
    inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] +
              a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
    inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] -
              a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
    inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] -
             a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
    inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] +
             a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
    inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] -
              a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
    inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] +
              a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];
    double determinant = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
    if (determinant == 0 || !std::isfinite(determinant)) {
        return NO;
    }
    for (int i = 0; i < 16; i++) {
        out[i / 4][i % 4] = inv[i] / determinant;
    }
    return YES;
}

static double ANNLength(const double v[3]) {
    return std::sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
}

static double ANNDot(const double a[3], const double b[3]) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}

// a·sa + b·sb into a.
static void ANNCombine(double a[3], const double b[3], double sa, double sb) {
    for (int i = 0; i < 3; i++) {
        a[i] = a[i] * sa + b[i] * sb;
    }
}

ANNTransformParts ANNTransformPartsIdentity(void) {
    ANNTransformParts parts = {};
    parts.scale[0] = parts.scale[1] = parts.scale[2] = 1;
    parts.perspective[3] = 1;
    parts.quaternion[3] = 1;
    return parts;
}

BOOL ANNTransformPartsOf(CATransform3D transform, ANNTransformParts *parts) {
    ANNMatrix m;
    ANNMatrixOf(transform, m);
    if (m[3][3] == 0) {
        return NO;
    }
    double w = m[3][3];
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            m[i][j] /= w;
        }
    }
    // The perspective's own matrix: the rest without the perspective column; it also tells a flat transform.
    ANNMatrix perspectiveMatrix;
    memcpy(perspectiveMatrix, m, sizeof(ANNMatrix));
    for (int i = 0; i < 3; i++) {
        perspectiveMatrix[i][3] = 0;
    }
    perspectiveMatrix[3][3] = 1;
    ANNMatrix inverse;
    if (!ANNMatrixInvert(perspectiveMatrix, inverse)) {
        return NO;
    }
    ANNTransformParts result = {};
    if (m[0][3] != 0 || m[1][3] != 0 || m[2][3] != 0) {
        const double right[4] = {m[0][3], m[1][3], m[2][3], m[3][3]};
        // right · (inverse transposed): each component is a row of the inverse dotted with `right`.
        for (int i = 0; i < 4; i++) {
            double sum = 0;
            for (int j = 0; j < 4; j++) {
                sum += right[j] * inverse[i][j];
            }
            result.perspective[i] = sum;
        }
    } else {
        result.perspective[3] = 1;
    }
    for (int i = 0; i < 3; i++) {
        result.translate[i] = m[3][i];
    }
    double row[3][3];
    for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
            row[i][j] = m[i][j];
        }
    }
    result.scale[0] = ANNLength(row[0]);
    if (result.scale[0] == 0) {
        return NO;
    }
    ANNCombine(row[0], row[0], 1 / result.scale[0], 0);
    result.skew[0] = ANNDot(row[0], row[1]);
    ANNCombine(row[1], row[0], 1, -result.skew[0]);
    result.scale[1] = ANNLength(row[1]);
    if (result.scale[1] == 0) {
        return NO;
    }
    ANNCombine(row[1], row[1], 1 / result.scale[1], 0);
    result.skew[0] /= result.scale[1];
    result.skew[1] = ANNDot(row[0], row[2]);
    ANNCombine(row[2], row[0], 1, -result.skew[1]);
    result.skew[2] = ANNDot(row[1], row[2]);
    ANNCombine(row[2], row[1], 1, -result.skew[2]);
    result.scale[2] = ANNLength(row[2]);
    if (result.scale[2] == 0) {
        return NO;
    }
    ANNCombine(row[2], row[2], 1 / result.scale[2], 0);
    result.skew[1] /= result.scale[2];
    result.skew[2] /= result.scale[2];
    // The rows are orthonormal now; a mirrored system turns into a rotation with every scale negated. A flat transform
    // (nothing out of the screen's plane) negates only x and keeps its rotation in the plane, as Android's views hold a
    // mirror: it then flips across the element's own axis on the way, on both platforms, rather than through 3D.
    const double cross[3] = {row[1][1] * row[2][2] - row[1][2] * row[2][1],
                             row[1][2] * row[2][0] - row[1][0] * row[2][2],
                             row[1][0] * row[2][1] - row[1][1] * row[2][0]};
    if (ANNDot(row[0], cross) < 0) {
        const double flatness = std::fabs(m[0][2]) + std::fabs(m[1][2]) + std::fabs(m[2][0]) +
                                std::fabs(m[2][1]) + std::fabs(m[0][3]) + std::fabs(m[1][3]) +
                                std::fabs(m[2][3]);
        const int negated = flatness < 1e-9 ? 1 : 3;
        for (int i = 0; i < negated; i++) {
            result.scale[i] = -result.scale[i];
            ANNCombine(row[i], row[i], -1, 0);
        }
    }
    // The quaternion by Shepperd's method, from the largest of its four terms: the signs of the others follow from
    // the off-diagonal terms, also for half a turn (w = 0), where the comparisons of the pseudo-code lose them. `r` is
    // the rotation for column vectors, the transpose of the rows.
    auto r = [&row](int i, int j) {
        return row[j][i];
    };
    double *q = result.quaternion;
    double trace = r(0, 0) + r(1, 1) + r(2, 2);
    if (trace > 0) {
        double s = 2 * std::sqrt(trace + 1);
        q[3] = s / 4;
        q[0] = (r(2, 1) - r(1, 2)) / s;
        q[1] = (r(0, 2) - r(2, 0)) / s;
        q[2] = (r(1, 0) - r(0, 1)) / s;
    } else if (r(0, 0) > r(1, 1) && r(0, 0) > r(2, 2)) {
        double s = 2 * std::sqrt(1 + r(0, 0) - r(1, 1) - r(2, 2));
        q[3] = (r(2, 1) - r(1, 2)) / s;
        q[0] = s / 4;
        q[1] = (r(0, 1) + r(1, 0)) / s;
        q[2] = (r(0, 2) + r(2, 0)) / s;
    } else if (r(1, 1) > r(2, 2)) {
        double s = 2 * std::sqrt(1 + r(1, 1) - r(0, 0) - r(2, 2));
        q[3] = (r(0, 2) - r(2, 0)) / s;
        q[0] = (r(0, 1) + r(1, 0)) / s;
        q[1] = s / 4;
        q[2] = (r(1, 2) + r(2, 1)) / s;
    } else {
        double s = 2 * std::sqrt(1 + r(2, 2) - r(0, 0) - r(1, 1));
        q[3] = (r(1, 0) - r(0, 1)) / s;
        q[0] = (r(0, 2) + r(2, 0)) / s;
        q[1] = (r(1, 2) + r(2, 1)) / s;
        q[2] = s / 4;
    }
    *parts = result;
    return YES;
}

// The rotation of a unit quaternion as a matrix of row vectors: the transpose of the decomposition's pseudo-code,
// whose matrix is written for column vectors there.
static void ANNRotationMatrixOf(const double q[4], ANNMatrix m) {
    double x = q[0], y = q[1], z = q[2], w = q[3];
    ANNMatrixIdentity(m);
    m[0][0] = 1 - 2 * (y * y + z * z);
    m[0][1] = 2 * (x * y + z * w);
    m[0][2] = 2 * (x * z - y * w);
    m[1][0] = 2 * (x * y - z * w);
    m[1][1] = 1 - 2 * (x * x + z * z);
    m[1][2] = 2 * (y * z + x * w);
    m[2][0] = 2 * (x * z + y * w);
    m[2][1] = 2 * (y * z - x * w);
    m[2][2] = 1 - 2 * (x * x + y * y);
}

// The shears as the decomposition took them off the rows: row 1 gains xy of row 0, row 2 xz of row 0 and yz of row 1.
static void ANNSkewMatrixOf(const double skew[3], ANNMatrix m) {
    ANNMatrixIdentity(m);
    m[1][0] = skew[0];
    m[2][0] = skew[1];
    m[2][1] = skew[2];
}

CATransform3D ANNTransformOfParts(ANNTransformParts parts) {
    // Built from the last step back: perspective, translation, rotation, skew, then the scale goes first.
    ANNMatrix m;
    ANNMatrixIdentity(m);
    for (int i = 0; i < 4; i++) {
        m[i][3] = parts.perspective[i];
    }
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 3; j++) {
            m[3][i] += parts.translate[j] * m[j][i];
        }
    }
    ANNMatrix rotation;
    ANNRotationMatrixOf(parts.quaternion, rotation);
    ANNMatrixMultiply(rotation, m, m);
    ANNMatrix skew;
    ANNSkewMatrixOf(parts.skew, skew);
    ANNMatrixMultiply(skew, m, m);
    for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 4; j++) {
            m[i][j] *= parts.scale[i];
        }
    }
    return ANNTransformOfMatrix(m);
}

static double ANNLerp(double from, double to, double f) {
    return from + f * (to - from);
}

ANNTransformParts ANNInterpolateTransformParts(ANNTransformParts from, ANNTransformParts to,
                                               double f) {
    if (f == 0) {
        return from;
    }
    if (f == 1) {
        return to;
    }
    ANNTransformParts parts;
    for (int i = 0; i < 3; i++) {
        parts.translate[i] = ANNLerp(from.translate[i], to.translate[i], f);
        parts.scale[i] = ANNLerp(from.scale[i], to.scale[i], f);
        parts.skew[i] = ANNLerp(from.skew[i], to.skew[i], f);
    }
    for (int i = 0; i < 4; i++) {
        parts.perspective[i] = ANNLerp(from.perspective[i], to.perspective[i], f);
    }
    // The slerp, along the shorter arc: q and −q are the same rotation.
    const double *a = from.quaternion;
    double b[4] = {to.quaternion[0], to.quaternion[1], to.quaternion[2], to.quaternion[3]};
    double product = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
    if (product < 0) {
        for (int i = 0; i < 4; i++) {
            b[i] = -b[i];
        }
        product = -product;
    }
    product = std::min(product, 1.0);
    if (product >= 1.0 - 1e-12) {
        for (int i = 0; i < 4; i++) {
            parts.quaternion[i] = ANNLerp(a[i], b[i], f);
        }
        return parts;
    }
    double theta = std::acos(product);
    double sinTheta = std::sqrt(1 - product * product);
    double wa = std::sin((1 - f) * theta) / sinTheta;
    double wb = std::sin(f * theta) / sinTheta;
    for (int i = 0; i < 4; i++) {
        parts.quaternion[i] = a[i] * wa + b[i] * wb;
    }
    return parts;
}

void ANNRotationDegreesOf(ANNTransformParts parts, double *x, double *y, double *z) {
    ANNMatrix r;
    ANNRotationMatrixOf(parts.quaternion, r);
    // r = Rx(a)·Ry(b)·Rz(c) in row vectors: r[0][2] = −sin b, r[1][2] = sin a·cos b, r[2][2] = cos a·cos b,
    // r[0][1] = cos b·sin c, r[0][0] = cos b·cos c.
    double sinB = std::max(-1.0, std::min(1.0, -r[0][2]));
    double b = std::asin(sinB);
    double a;
    double c;
    if (std::fabs(std::cos(b)) > 1e-6) {
        a = std::atan2(r[1][2], r[2][2]);
        c = std::atan2(r[0][1], r[0][0]);
    } else {
        // Turned a quarter about y: x and z turn about one axis, all of it goes to x.
        c = 0;
        a = std::atan2(sinB * r[1][0], r[1][1]);
    }
    const double degrees = 180.0 / M_PI;
    *x = a * degrees;
    *y = b * degrees;
    *z = c * degrees;
}

CATransform3D ANNSkewTransformOf(ANNTransformParts parts) {
    ANNMatrix m;
    ANNSkewMatrixOf(parts.skew, m);
    return ANNTransformOfMatrix(m);
}

#if DEBUG

static BOOL ANNSameTransform(CATransform3D a, CATransform3D b, double tolerance) {
    ANNMatrix ma;
    ANNMatrix mb;
    ANNMatrixOf(a, ma);
    ANNMatrixOf(b, mb);
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            if (!(std::fabs(ma[i][j] - mb[i][j]) <= tolerance)) {
                return NO;
            }
        }
    }
    return YES;
}

static void ANNExpectTransform(const char *what, CATransform3D value, CATransform3D expected) {
    if (!ANNSameTransform(value, expected, 1e-6)) {
        RCTLogWarn(@"[ApexNavigation] transform self-check: %s is off", what);
    }
}

static CATransform3D ANNRotationZ(double degrees) {
    return CATransform3DMakeRotation(degrees * M_PI / 180.0, 0, 0, 1);
}

// The transform at f of the way from `from` to `to`, split and rebuilt.
static CATransform3D ANNTransformBetween(CATransform3D from, CATransform3D to, double f) {
    ANNTransformParts a;
    ANNTransformParts b;
    if (!ANNTransformPartsOf(from, &a) || !ANNTransformPartsOf(to, &b)) {
        return CATransform3DIdentity;
    }
    return ANNTransformOfParts(ANNInterpolateTransformParts(a, b, f));
}

void ANNTransformPartsSelfCheck(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      CATransform3D tilt = CATransform3DMakeRotation(40 * M_PI / 180.0, 1, 0, 0);
      tilt.m34 = -1.0 / 500;
      CATransform3D shear = CATransform3DIdentity;
      shear.m21 = 0.3;
      const struct {
          const char *name;
          CATransform3D transform;
      } splits[] = {
          {"a rotation of 90", ANNRotationZ(90)},
          {"a rotation of 180", ANNRotationZ(180)},
          {"a scale, a rotation and a translation",
           CATransform3DConcat(CATransform3DConcat(CATransform3DMakeScale(2, 0.5, 1),
                                                   ANNRotationZ(30)),
                               CATransform3DMakeTranslation(12, -7, 0))},
          {"a skew", CATransform3DConcat(shear, ANNRotationZ(-20))},
          {"a tilt with perspective", tilt},
          {"a mirror", CATransform3DMakeScale(-1, 1, 1)},
          {"a mirror and a turn of 30",
           CATransform3DConcat(ANNRotationZ(30), CATransform3DMakeScale(-1, 1, 1))},
          {"a turn of -90 and a mirror",
           CATransform3DConcat(CATransform3DMakeScale(-1, 1, 1), ANNRotationZ(-90))},
          {"a mirror upside down and a turn of -30",
           CATransform3DConcat(ANNRotationZ(-30), CATransform3DMakeScale(1, -1, 1))},
          {"half a turn and a tilt",
           CATransform3DConcat(ANNRotationZ(180),
                               CATransform3DMakeRotation(20 * M_PI / 180.0, 1, 0, 0))},
      };
      for (const auto &split : splits) {
          ANNTransformParts parts;
          if (!ANNTransformPartsOf(split.transform, &parts)) {
              RCTLogWarn(@"[ApexNavigation] transform self-check: %s can't be split", split.name);
              continue;
          }
          ANNExpectTransform(split.name, ANNTransformOfParts(parts), split.transform);
      }
      ANNExpectTransform("0 to 90 half way",
                         ANNTransformBetween(ANNRotationZ(0), ANNRotationZ(90), 0.5),
                         ANNRotationZ(45));
      ANNExpectTransform("45 to -45 half way",
                         ANNTransformBetween(ANNRotationZ(45), ANNRotationZ(-45), 0.5),
                         CATransform3DIdentity);
      ANNExpectTransform("0 to 270 half way, the shorter arc",
                         ANNTransformBetween(ANNRotationZ(0), ANNRotationZ(270), 0.5),
                         ANNRotationZ(-45));
      ANNExpectTransform("170 to -170 half way, the shorter arc",
                         ANNTransformBetween(ANNRotationZ(170), ANNRotationZ(-170), 0.5),
                         ANNRotationZ(180));
      ANNExpectTransform("a scale half way",
                         ANNTransformBetween(CATransform3DIdentity,
                                             CATransform3DMakeScale(3, 2, 1), 0.5),
                         CATransform3DMakeScale(2, 1.5, 1));
      // Half a turn keeps its size on the way: a quarter turn, either way, half way.
      CATransform3D half = ANNTransformBetween(ANNRotationZ(0), ANNRotationZ(180), 0.5);
      if (!ANNSameTransform(half, ANNRotationZ(90), 1e-6) &&
          !ANNSameTransform(half, ANNRotationZ(-90), 1e-6)) {
          RCTLogWarn(@"[ApexNavigation] transform self-check: 0 to 180 half way is off");
      }
      // A flat mirror turned keeps its rotation in the plane, the mirror in x, as Android's views hold it.
      ANNTransformParts mirrored;
      CATransform3D mirror =
          CATransform3DConcat(ANNRotationZ(30), CATransform3DMakeScale(-1, 1, 1));
      if (ANNTransformPartsOf(mirror, &mirrored)) {
          BOOL flat = std::fabs(mirrored.quaternion[0]) < 1e-9 &&
                      std::fabs(mirrored.quaternion[1]) < 1e-9;
          if (!(mirrored.scale[0] < 0 && mirrored.scale[1] > 0 && flat)) {
              RCTLogWarn(@"[ApexNavigation] transform self-check: a flat mirror leaves the plane");
          }
      }
      // The turns about x, y and z rebuild the rotation in the order of a view's tracks.
      CATransform3D turned = CATransform3DConcat(
          CATransform3DConcat(CATransform3DMakeRotation(25 * M_PI / 180.0, 1, 0, 0),
                              CATransform3DMakeRotation(-35 * M_PI / 180.0, 0, 1, 0)),
          ANNRotationZ(120));
      ANNTransformParts turnedParts;
      if (ANNTransformPartsOf(turned, &turnedParts)) {
          double x = 0, y = 0, z = 0;
          ANNRotationDegreesOf(turnedParts, &x, &y, &z);
          CATransform3D rebuilt = CATransform3DConcat(
              CATransform3DConcat(CATransform3DMakeRotation(x * M_PI / 180.0, 1, 0, 0),
                                  CATransform3DMakeRotation(y * M_PI / 180.0, 0, 1, 0)),
              ANNRotationZ(z));
          ANNExpectTransform("the turns about x, y and z", rebuilt, turned);
      }
    });
}

#endif
