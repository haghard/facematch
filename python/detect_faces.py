import tensorflow as tf
import detect_face
import facenet
import cv2
import imutils
import numpy as np
import argparse

executionFolder = "/Volumes/dev/projects/scala/play-silhouette-face-authentication/python/"

parser = argparse.ArgumentParser()
parser.add_argument("--img", type = str, required=True)
parser.add_argument("--face", type = str, required=True)

args = parser.parse_args()

# some constants kept as default from facenet
minsize = 20
threshold = [0.6, 0.7, 0.7]
factor = 0.709
margin = 44
input_image_size = 160

sess = tf.Session()

# read pnet, rnet, onet models from align directory and files are det1.npy, det2.npy, det3.npy
pnet, rnet, onet = detect_face.create_mtcnn(sess, executionFolder + 'align')


# read 20170512-110547 model file downloaded from https://drive.google.com/file/d/0B5MzpY9kBtDVZ2RpVDYwWmxoSUk
facenet.load_model(executionFolder + "20170512-110547/20170512-110547.pb")

# Get input and output tensors
images_placeholder = tf.get_default_graph().get_tensor_by_name("input:0")
embeddings = tf.get_default_graph().get_tensor_by_name("embeddings:0")
phase_train_placeholder = tf.get_default_graph().get_tensor_by_name("phase_train:0")
embedding_size = embeddings.get_shape()[1]

def getFace(img):
    faces = []
    img_size = np.asarray(img.shape)[0:2]
    bounding_boxes, points = detect_face.detect_face(img, minsize, pnet, rnet, onet, threshold, factor)
    if not len(bounding_boxes) == 0:
        for face in bounding_boxes:
            if face[4] > 0.50:
                det = np.squeeze(face[0:4])
                bb = np.zeros(4, dtype=np.int32)
                bb[0] = np.maximum(det[0] - margin / 2, 0)
                bb[1] = np.maximum(det[1] - margin / 2, 0)
                bb[2] = np.minimum(det[2] + margin / 2, img_size[1])
                bb[3] = np.minimum(det[3] + margin / 2, img_size[0])
                cropped = img[bb[1]:bb[3], bb[0]:bb[2], :]
                resized = cv2.resize(cropped, (input_image_size,input_image_size),interpolation = cv2.INTER_CUBIC)
                prewhitened = facenet.prewhiten(resized)
                faces.append({'face':resized,'rect':[bb[0],bb[1],bb[2],bb[3]],'embedding':getEmbedding(prewhitened)})
    return faces

def data_uri_to_cv2_img(base64Uri):
    nparr = np.fromstring(base64Uri.decode('base64'), np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    return img

def getEmbedding(resized):
    reshaped = resized.reshape(-1,input_image_size,input_image_size,3)
    feed_dict = {images_placeholder: reshaped, phase_train_placeholder: False}
    # print(feed_dict)
    embedding = sess.run(embeddings, feed_dict=feed_dict)
    return embedding


def compare2face(face1, orig, ind):
    dist = np.sqrt(np.sum(np.square(np.subtract(face1, orig))))
    print("{0:.0f}: {1:.3f}".format(ind, dist))
    return dist


orgImg = data_uri_to_cv2_img(args.face)
orgImgResize = imutils.resize(orgImg, width=200)
orgFace = getFace(orgImg)

img = cv2.imread(args.img)
img = imutils.resize(img, width=1000)
faces = getFace(img)

ind = 1
for face in faces:
    compare2face(face['embedding'], orgFace[0]['embedding'], ind)
    #cv2.rectangle(img, (face['rect'][0], face['rect'][1]), (face['rect'][2], face['rect'][3]), (0, 255, 0), ind)
    ind = ind * 2
#cv2.imshow("faces", img)
#cv2.waitKey(0)
#cv2.destroyAllWindows()

#compare2face(face['embedding'], orgFace[0]['embedding'])
#print("faceN: " + str(face['embedding'].size))
#print("face-origin: " + str(orgFace[0]['embedding'].size))
#dist = np.sqrt(np.sum(np.square(np.subtract(face['embedding'], orgFace[0]['embedding']))))
#print("{0:.3f}".format(dist))

#if face1 and face2:
#    dist = np.sqrt(np.sum(np.square(np.subtract(face1[0]['embedding'], face2[0]['embedding']))))
#    return dist
#return -1